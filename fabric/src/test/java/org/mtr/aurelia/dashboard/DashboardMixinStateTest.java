package org.mtr.aurelia.dashboard;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * In a pack with MAGIC, the dashboard crashed on open and on Esc out of the Aurelia panel: {@code atr$tabBar()} returned
 * null because the mixin's field initialiser never ran on that {@code DashboardScreen}. The mixin's state must be
 * created on first use, never by a field initialiser.
 */
public final class DashboardMixinStateTest {

	private static final String MIXIN = "org/mtr/atr/mixin/AureliaDashboardMixin";

	@Test
	public void dashboardMixinStateDoesNotDependOnFieldInitialisers() throws IOException {
		final ClassNode classNode = new ClassNode();
		try (final InputStream inputStream = DashboardMixinStateTest.class.getResourceAsStream("/" + MIXIN + ".class")) {
			Assertions.assertNotNull(inputStream, MIXIN + ".class is not on the test classpath");
			new ClassReader(inputStream).accept(classNode, 0);
		}

		final List<String> initialised = new ArrayList<>();
		for (final MethodNode method : classNode.methods) {
			if (!method.name.equals("<init>")) {
				continue;
			}
			for (final AbstractInsnNode instruction : method.instructions) {
				if (instruction.getOpcode() == Opcodes.PUTFIELD && instruction instanceof FieldInsnNode field && field.owner.equals(MIXIN)) {
					initialised.add(field.name);
				}
			}
		}
		Assertions.assertEquals(List.of(), initialised, "fields set by a mixin field initialiser, which may never run");
	}
}
