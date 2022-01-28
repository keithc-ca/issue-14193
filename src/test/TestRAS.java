package test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.ibm.dtfj.image.Image;
import com.ibm.dtfj.image.ImageFactory;
import com.ibm.jvm.Dump;
import com.ibm.jvm.InvalidDumpOptionException;

public class TestRAS {

	private static final byte[] ClassHeapDump = "// Version: ".getBytes(StandardCharsets.US_ASCII);

	private static final byte[] JavaDump = "0SECTION       TITLE".getBytes(StandardCharsets.US_ASCII);

	private static final byte[] PortableHeapDump = "portable heap dump".getBytes(StandardCharsets.US_ASCII);

	private static final byte[] SnapDump = "UTTH".getBytes(StandardCharsets.US_ASCII);

	private static final byte[] ThreadEqual = "Thread= ".getBytes(StandardCharsets.US_ASCII);

	private static void aggressiveGC() {
		Runtime runtime = Runtime.getRuntime();

		for (int i = 0; i < 32; ++i) {
			runtime.gc();
		}
	}

	private static String getContentType(File file) {
		try (FileInputStream input = new FileInputStream(file)) {
			byte[] firstBytes = new byte[64];
			int length = input.read(firstBytes);

			if (Arrays.equals(firstBytes, 0, length, ClassHeapDump, 0, ClassHeapDump.length)) {
				return "heap.classic";
			} else if (Arrays.equals(firstBytes, 0, length, JavaDump, 0, JavaDump.length)) {
				return "javacore";
			} else if (Arrays.equals(firstBytes, 2, length - 2, PortableHeapDump, 0, PortableHeapDump.length)) {
				return "phd";
			} else if (Arrays.equals(firstBytes, 0, length, SnapDump, 0, SnapDump.length)) {
				return "snap";
			} else if (Arrays.equals(firstBytes, 0, length, ThreadEqual, 0, ThreadEqual.length)) {
				ByteArrayOutputStream contents = new ByteArrayOutputStream((int) file.length());

				contents.write(firstBytes, 0, length);

				for (byte[] buffer = new byte[1024];;) {
					int read = input.read(buffer);

					if (read > 0) {
						contents.write(buffer, 0, read);
					} else {
						break;
					}
				}

				String[] threads = new String(contents.toByteArray(), StandardCharsets.US_ASCII).split("Thread=");

				if (threads.length == 2) {
					return "stack";
				} else if (threads.length > 2) {
					return "console";
				}
			}

			Image image = null;

			try {
				Class<?> factoryClass = Class.forName("com.ibm.dtfj.image.j9.ImageFactory");
				@SuppressWarnings("deprecation")
				ImageFactory factory = (ImageFactory) factoryClass.newInstance();

				image = factory.getImage(file);

				if (image != null) {
					return "system";
				}
			} finally {
				if (image != null) {
					image.close();
				}
			}
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
			e.printStackTrace(); // ignore
		} catch (IOException e) {
			return null;
		}

		return null;
	}

	public static void main(String[] args) {
		boolean error = true;
		File file = null;
		boolean gc = false;

		for (String arg : args) {
			if (arg.equals("-gc")) {
				gc = true;
			} else if (file == null) {
				file = new File(arg);
				error = false;
			} else {
				error = true;
			}
		}

		if (error) {
			System.out.println("Usage: test.TestRAS [-gc] core.dmp");
			return;
		}

		showGCPolicy();

		for (int iteration = 0; ++iteration <= 100;) {
			System.out.format("Iteration %d:%n", iteration);

			if (gc) {
				aggressiveGC();
			}

			showHeapStats();

			try {
				String type = getContentType(file);

				System.out.format("  dump type is '%s'.%n", type);
			} catch (OutOfMemoryError e) {
				System.out.println("Out of memory.");
				try {
					Dump.systemDumpToFile("core.test.dmp");
					Dump.heapDumpToFile("heapdump.test.phd");
					Dump.javaDumpToFile("javacore.test.txt");
				} catch (InvalidDumpOptionException invalid) {
					invalid.printStackTrace();
				}
				break;
			}
		}
	}

	private static void showGCPolicy() {
		java.lang.management.MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

		if (memory instanceof com.ibm.lang.management.MemoryMXBean) {
			String mode = ((com.ibm.lang.management.MemoryMXBean) memory).getGCMode();

			System.out.format("Using GC policy '%s'.%n", mode);
		}
	}

	private static void showHeapStats() {
		double MB = 1024.0 * 1024.0;
		Runtime runtime = Runtime.getRuntime();
		double freeMB = runtime.freeMemory() / MB;
		double totalMB = runtime.totalMemory() / MB;
		double maxMB = runtime.maxMemory() / MB;

		System.out.format("  memory: free=%.1f MB total=%.1f MB max=%.1f MB%n", freeMB, totalMB, maxMB);
	}

}
