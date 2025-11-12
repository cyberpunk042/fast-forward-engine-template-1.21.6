package net.cyberpunk042;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class FastforwardengineClient implements ClientModInitializer {
	private static boolean applied = false;
	private static Integer savedRenderDistance = null;
	private static Object savedParticles = null;
	private static Object savedClouds = null;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client == null || client.options == null) return;
			boolean wantHeadless = Fastforwardengine.CONFIG.clientHeadlessDuringWarp && Fastforwardengine.isFastForwardRunning();
			try {
				if (wantHeadless && !applied) {
					applied = true;
					// Save
					savedRenderDistance = getRenderDistance(client.options);
					savedClouds = getOptionEnumValue(client.options, "cloud");
					savedParticles = getOptionEnumValue(client.options, "particle");
					// Apply minimal
					setRenderDistance(client.options, 2);
					setOptionEnumValue(client.options, "cloud", "OFF");
					setOptionEnumValue(client.options, "particle", "MINIMAL");
				} else if (!wantHeadless && applied) {
					applied = false;
					// Restore
					if (savedRenderDistance != null) setRenderDistance(client.options, savedRenderDistance);
					if (savedClouds != null) setOptionEnumValue(client.options, "cloud", savedClouds);
					if (savedParticles != null) setOptionEnumValue(client.options, "particle", savedParticles);
					savedRenderDistance = null; savedParticles = null; savedClouds = null;
				}
			} catch (Throwable ignored) {}
		});
	}
	
	private static Object findOptionInstance(Object options, String nameContains) {
		try {
			for (var m : options.getClass().getMethods()) {
				if (!m.getName().toLowerCase().contains(nameContains)) continue;
				if (m.getParameterCount() != 0) continue;
				try {
					Object opt = m.invoke(options);
					// Heuristic: OptionInstance has get()/set(T)
					var clazz = opt.getClass();
					clazz.getMethod("get");
					clazz.getMethod("set", Object.class);
					return opt;
				} catch (Throwable ignored) {}
			}
		} catch (Throwable ignored) {}
		return null;
	}

	private static Integer getRenderDistance(Object options) {
		try {
			Object inst = findOptionInstance(options, "renderdistance");
			if (inst == null) inst = findOptionInstance(options, "render");
			if (inst == null) return null;
			Object val = inst.getClass().getMethod("get").invoke(inst);
			if (val instanceof Integer i) return i;
			return null;
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static void setRenderDistance(Object options, int value) {
		try {
			Object inst = findOptionInstance(options, "renderdistance");
			if (inst == null) inst = findOptionInstance(options, "render");
			if (inst == null) return;
			inst.getClass().getMethod("set", Object.class).invoke(inst, Integer.valueOf(value));
		} catch (Throwable ignored) {}
	}

	private static Object getOptionEnumValue(Object options, String contains) {
		try {
			Object inst = findOptionInstance(options, contains);
			if (inst == null) return null;
			return inst.getClass().getMethod("get").invoke(inst);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static void setOptionEnumValue(Object options, String contains, String desired) {
		try {
			Object inst = findOptionInstance(options, contains);
			if (inst == null) return;
			Object current = inst.getClass().getMethod("get").invoke(inst);
			if (current == null) return;
			Class<?> enumCls = current.getClass();
			if (!enumCls.isEnum()) return;
			Object target = null;
			for (Object c : enumCls.getEnumConstants()) {
				if (String.valueOf(c).equalsIgnoreCase(desired)) { target = c; break; }
			}
			if (target != null) {
				inst.getClass().getMethod("set", Object.class).invoke(inst, target);
			}
		} catch (Throwable ignored) {}
	}

	private static void setOptionEnumValue(Object options, String contains, Object saved) {
		try {
			Object inst = findOptionInstance(options, contains);
			if (inst == null) return;
			inst.getClass().getMethod("set", Object.class).invoke(inst, saved);
		} catch (Throwable ignored) {}
	}
}