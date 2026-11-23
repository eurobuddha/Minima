package org.minima.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.minima.objects.Address;
import org.minima.objects.base.MiniData;
import org.minima.utils.bondserver.BondServer;
import org.minima.utils.cevt.CEVTServer;
import org.minima.utils.json.JSONArray;

import com.sun.net.httpserver.HttpServer;

/**
 * BondServer and CEVTServer put a request coin's state - written by its sender - into commands they send over
 * the RPC (2026-11-23): only the expected shapes are used, and a transaction goes one command per request.
 */
public class ServerStateTest {

	static final String HEX = "0x2868AE4C92C22C5CDAF223574203F2906E8AA92A17050CD99D851EC5DBB61B66";

	@Test
	public void onlyAnAddressIsAnAddress() {
		String mx = Address.makeMinimaAddress(new MiniData(HEX));
		assertTrue(BondServer.isAddress(HEX));
		assertTrue(BondServer.isAddress(mx));

		assertFalse(BondServer.isAddress(null));
		assertFalse(BondServer.isAddress(HEX+";send address:"+HEX+" amount:1"));
		assertFalse(BondServer.isAddress(HEX+" port:7"));
		assertFalse(BondServer.isAddress(HEX.substring(0, 60)));
		assertFalse(BondServer.isAddress("\""+HEX+"\""));
		//one character changed: the checksum no longer matches
		String bad = mx.substring(0, mx.length()-1)+(mx.endsWith("0") ? "1" : "0");
		assertFalse(BondServer.isAddress(bad));
		assertFalse(BondServer.isAddress(Address.makeMinimaAddress(new MiniData("0x0102"))));
	}

	@Test
	public void cevtStateHasKnownShapes() {
		assertNull(CEVTServer.invalidState(HEX, "terminal-7", "0x30819F"));
		assertEquals("address", CEVTServer.invalidState("0x01;quit", "t", "0x01"));
		assertEquals("terminal id", CEVTServer.invalidState(HEX, "t;send amount:1", "0x01"));
		assertEquals("terminal id", CEVTServer.invalidState(HEX, "t port:9", "0x01"));
		assertEquals("terminal id", CEVTServer.invalidState(HEX, null, "0x01"));
		assertEquals("public key", CEVTServer.invalidState(HEX, "t", "0x01 privatekey:0x02"));
		assertEquals("public key", CEVTServer.invalidState(HEX, "t", "[0x01]"));
	}

	/** A stand-in RPC: records each request, fails any txnstate */
	static HttpServer rpc(List<String> zSeen) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", ex -> {
			String command = URLDecoder.decode(ex.getRequestURI().getRawPath().substring(1), "UTF-8");
			zSeen.add(command);
			byte[] reply = ("{\"command\":\"x\",\"status\":"+!command.startsWith("txnstate")+"}").getBytes("UTF-8");
			ex.sendResponseHeaders(200, reply.length);
			ex.getResponseBody().write(reply);
			ex.close();
		});
		server.start();
		return server;
	}

	@Test
	public void transactionGoesOneCommandPerRequestAndIsDeletedOnFailure() throws Exception {
		List<String> seen = new ArrayList<>();
		HttpServer server = rpc(seen);
		String host = "http://127.0.0.1:"+server.getAddress().getPort()+"/";
		ArrayList<String> txn = new ArrayList<>(Arrays.asList(
				"txncreate id:0x01", "txninput id:0x01 coinid:0x02", "txnstate id:0x01 port:2 value:"+HEX,
				"txnsign id:0x01 publickey:auto", "txndelete id:0x01"));
		String oldbond = BondServer.mHost, oldcevt = CEVTServer.mHost;
		try {
			BondServer.mHost = host;
			JSONArray res = BondServer.runTxnCommands("0x01", txn);
			assertEquals(3, res.size());
			assertEquals(Arrays.asList("txncreate id:0x01", "txninput id:0x01 coinid:0x02",
					"txnstate id:0x01 port:2 value:"+HEX, "txndelete id:0x01"), seen);

			seen.clear();
			CEVTServer.mHost = host;
			res = CEVTServer.runTxnCommands("0x01", txn);
			assertEquals(3, res.size());
			assertEquals("txndelete id:0x01", seen.get(seen.size()-1));
			for(String command : seen) {
				assertFalse(command.contains(";"));
			}
		}finally {
			BondServer.mHost = oldbond;
			CEVTServer.mHost = oldcevt;
			server.stop(0);
		}
	}
}
