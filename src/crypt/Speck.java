package crypt;

import java.nio.ByteBuffer;

public class Speck {
	// Default 27 Real rounds for Speck 64/128
	private static int ROUNDSx2 = 54;
	// Size in bytes
	private static final int KEYSIZE = 32;
	private static final int BLOCKSIZE = 16;
	private static final int WORDSIZE = BLOCKSIZE / 2;

	private static void BytesToWords32(byte[] b, int[] w) {
		for (int i = 0; i < w.length; i++) {
			w[i] = ByteBuffer.wrap(new byte[] { b[i * 4 + 3], b[i * 4 + 2], b[i * 4 + 1], b[i * 4] }).getInt();
		}
	}

	private static void Words32ToBytes(int[] words, byte[] bytes) {
		int i;
		for (i = 0; i < words.length; i++) {
			bytes[i * 4] = (byte) words[i];
			bytes[i * 4 + 1] = (byte) (words[i] >>> 8);
			bytes[i * 4 + 2] = (byte) (words[i] >>> 16);
			bytes[i * 4 + 3] = (byte) (words[i] >>> 24);
		}
	}

	private static void Speck64128KeySchedule(int[] K, int[] rk) {
		int i;
		int c = (int) 0xfffffffc;
		long z = 0xfc2ce51207a635dbL;
		rk[0] = K[0];
		rk[1] = K[1];
		rk[2] = K[2];
		rk[3] = K[3];
		for (i = 4; i < ROUNDSx2; i++) {
			rk[i] = (int) (c ^ (z & 1) ^ rk[i - 4] ^ (((rk[i - 1]) >>> (3)) | ((rk[i - 1]) << (32 - (3)))) ^ rk[i - 3]
					^ (((rk[i - 1]) >>> (4)) | ((rk[i - 1]) << (32 - (4))))
					^ (((rk[i - 3]) >>> (1)) | ((rk[i - 3]) << (32 - (1)))));
			z >>>= 1;
		}
	}

	private static void Speck64128Encrypt(int[] Pt, int[] Ct, int[] rk, boolean debug) {
		int i;
		Ct[1] = Pt[1];
		Ct[0] = Pt[0];
		if (debug) {
			System.out.println("\nEncrypt Schedule with " + ROUNDSx2 / 2 + " rounds:");
		}
		for (i = 0; i < ROUNDSx2;) {
			Ct[0] ^= (((((Ct[1]) << (1)) | (Ct[1] >>> (32 - (1)))) & (((Ct[1]) << (8)) | (Ct[1] >>> (32 - (8)))))
					^ (((Ct[1]) << (2)) | (Ct[1] >>> (32 - (2)))));
			Ct[0] ^= rk[i++];
			if (debug) {
				System.out.printf("              %08X ", Ct[0]);
				System.out.printf("%08X", Ct[1]);
			}
			Ct[1] ^= (((((Ct[0]) << (1)) | (Ct[0] >>> (32 - (1)))) & (((Ct[0]) << (8)) | (Ct[0] >>> (32 - (8)))))
					^ (((Ct[0]) << (2)) | (Ct[0] >>> (32 - (2)))));
			Ct[1] ^= rk[i++];
			if (debug) {
				System.out.printf(" %08X ", Ct[1]);
				System.out.printf("%08X\n", Ct[0]);
			}
		}
	}

	private static void Speck64128Decrypt(int[] Pt, int[] Ct, int[] rk) {
		int i;
		Pt[1] = Ct[1];
		Pt[0] = Ct[0];
		for (i = (ROUNDSx2 - 1); i >= 0;) {
			Pt[1] ^= (((((Pt[0]) << (1)) | (Pt[0] >>> (32 - (1)))) & (((Pt[0]) << (8)) | (Pt[0] >>> (32 - (8)))))
					^ (((Pt[0]) << (2)) | (Pt[0] >>> (32 - (2)))));
			Pt[1] ^= rk[i--];
			Pt[0] ^= (((((Pt[1]) << (1)) | (Pt[1] >>> (32 - (1)))) & (((Pt[1]) << (8)) | (Pt[1] >>> (32 - (8)))))
					^ (((Pt[1]) << (2)) | (Pt[1] >>> (32 - (2)))));
			Pt[0] ^= rk[i--];
		}
	}

	public static byte[] encrypt(byte[] plain, byte[] key, boolean debug, boolean deepDebug) {
		byte[] cipher = new byte[plain.length];

		// Key
		int[] K = new int[KEYSIZE / 8];
		BytesToWords32(key, K);
		// Key Schedule
		int[] rk = new int[ROUNDSx2];
		Speck64128KeySchedule(K, rk);

		if (deepDebug) {
			System.out.print("\nKey:");
			printHex(K);
			System.out.print("\nKeySchedule:");
			printHex(rk);
		}

		byte[] pt = new byte[WORDSIZE];
		byte[] ct = new byte[WORDSIZE];
		int[] Pt = new int[WORDSIZE / 4];
		int[] Ct = new int[WORDSIZE / 4];

		int start;

		for (int nblock = 0; nblock < plain.length / WORDSIZE; nblock++) {
			start = nblock * WORDSIZE;
			System.arraycopy(plain, start, pt, 0, WORDSIZE);
			BytesToWords32(pt, Pt);
			Speck64128Encrypt(Pt, Ct, rk, deepDebug && nblock == 0);
			Words32ToBytes(Ct, ct);
			System.arraycopy(ct, 0, cipher, start, WORDSIZE);
			if (debug) {
				if (nblock == 0) {
					System.out.print("\nCipher:");
				}
				printHex(Ct);
			}
		}

		return cipher;
	}

	public static byte[] decrypt(byte[] cipher, byte[] key, boolean debug) {
		byte[] plain = new byte[cipher.length];

		// Key
		int[] K = new int[4];
		BytesToWords32(key, K);
		// Key Schedule
		int[] rk = new int[ROUNDSx2];
		Speck64128KeySchedule(K, rk);

		int[] Ct = new int[WORDSIZE / 4];
		int[] PtD = new int[WORDSIZE / 4];
		byte[] ct = new byte[WORDSIZE];
		byte[] ptD = new byte[WORDSIZE];
		int start;

		for (int nblock = 0; nblock < cipher.length / WORDSIZE; nblock++) {
			start = nblock * WORDSIZE;
			System.arraycopy(cipher, start, ct, 0, WORDSIZE);
			BytesToWords32(ct, Ct);
			Speck64128Decrypt(PtD, Ct, rk);
			Words32ToBytes(PtD, ptD);
			System.arraycopy(ptD, 0, plain, start, WORDSIZE);
			if (debug) {
				if (nblock == 0) {
					System.out.print("\nPlain:");
				}
				printHex(PtD);
			}
		}

		return plain;
	}

	public static byte[] encrypt(byte[] plain, byte[] key, boolean debug) {
		return encrypt(plain, key, debug, false);
	}

	public static byte[] encrypt(byte[] plain, byte[] key) {
		return encrypt(plain, key, false, false);
	}

	public static byte[] decrypt(byte[] cipher, byte[] key) {
		return decrypt(cipher, key, false);
	}

	public static void main(String[] args) {
		ROUNDSx2 = 44;
		String message = "und like";
		// byte[] pt = {0x75, 0x6e, 0x64, 0x20, 0x6c, 0x69, 0x6b, 0x65};

		byte[] pt = message.getBytes();

		// Key
		byte[] k = { 0x00, 0x01, 0x02, 0x03, 0x08, 0x09, 0x0a, 0x0b, 0x10, 0x11, 0x12, 0x13, 0x18, 0x19, 0x1a, 0x1b };

		byte[] ct = encrypt(pt, k, true, true);

		byte[] ptD = decrypt(ct, k, true);

		System.out.println("\nMessage:\n              " + new String(ptD));
	}

	private static void printHex(int[] hex) {
		System.out.print("\n              ");
		for (int i = 0; i < hex.length; i++) {
			System.out.printf("%08X ", hex[i]);
			if ((i + 1) % 4 == 0) {
				System.out.print("\n              ");
			}
		}
	}

}
