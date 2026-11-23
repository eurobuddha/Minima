package org.minima.system.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.lang.reflect.Field;

import org.junit.Test;
import org.minima.database.MinimaDB;
import org.minima.system.Main;
import org.minima.system.commands.maxima.maxcontacts;
import org.minima.system.network.maxima.MaximaManager;
import org.minima.utils.json.JSONObject;

/**
 * maxcontacts action:import adds each contact of the list - as ONE parameter value, never as command text
 * where a TAB (the list only drops spaces) started a parameter of its own (2026-11-23).
 */
public class MaxcontactsImportTest {

	/** maxcontacts with its add recorded instead of run */
	static class RecordingMaxcontacts extends maxcontacts {
		static JSONObject LAST;
		@Override
		public JSONObject runCommand() throws Exception {
			if("add".equals(getParams().get("action"))) {
				LAST = getParams();
				return getJSONReply();
			}
			return super.runCommand();
		}
		@Override
		public Command getFunction() {
			return new RecordingMaxcontacts();
		}
	}

	@Test
	public void importedContactCannotAddParameters() throws Exception {
		MinimaDB.createDB();
		Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
		unsafeField.setAccessible(true);
		sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
		Main main = (Main) unsafe.allocateInstance(Main.class);
		MaximaManager max = (MaximaManager) unsafe.allocateInstance(MaximaManager.class);
		Field inited = MaximaManager.class.getDeclaredField("mInited");
		inited.setAccessible(true);
		inited.set(max, true);
		Field maxima = Main.class.getDeclaredField("mMaxima");
		maxima.setAccessible(true);
		maxima.set(main, max);
		Field inst = Main.class.getDeclaredField("mMainInstance");
		inst.setAccessible(true);
		inst.set(null, main);

		Command real = CommandInjectionTest.swap("maxcontacts", new RecordingMaxcontacts());
		try {
			RecordingMaxcontacts.LAST = null;
			CommandRunner.getRunner().runMultiCommand("maxcontacts action:import contactlist:\"MxCONTACT\tid:7\"");
			assertEquals("MxCONTACT\tid:7", RecordingMaxcontacts.LAST.get("contact"));
			assertFalse("the TAB added id:7 to the add", RecordingMaxcontacts.LAST.containsKey("id"));
		}finally {
			CommandInjectionTest.swap("maxcontacts", real);
			inst.set(null, null);
		}
	}
}
