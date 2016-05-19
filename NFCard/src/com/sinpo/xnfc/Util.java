/* NFCard is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or
(at your option) any later version.

NFCard is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Wget.  If not, see <http://www.gnu.org/licenses/>.

Additional permission under GNU GPL version 3 section 7 */

package com.sinpo.xnfc;

public final class Util {
	
	/**
	 * fail result for find operation.
	 */
	public static final short TAG_NOT_FOUND = -1;
	
	private final static char[] HEX = { '0', '1', '2', '3', '4', '5', '6', '7',
			'8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	private Util() {
	}

	public static byte[] toBytes(int a) {
		return new byte[] { (byte) (0x000000ff & (a >>> 24)),
				(byte) (0x000000ff & (a >>> 16)),
				(byte) (0x000000ff & (a >>> 8)), (byte) (0x000000ff & (a)) };
	}

	public static int toInt(byte[] b, int s, int n) {
		int ret = 0;

		final int e = s + n;
		for (int i = s; i < e; ++i) {
			ret <<= 8;
			ret |= b[i] & 0xFF;
		}
		return ret;
	}
	
	public static int BCDtoInt(byte[] b, int s, int n) {
		int ret = 0;
		int tmp;

		final int e = s + n;
		for (int i = s; i < e; ++i) {
			tmp = (b[i] >> 4) & 0x0F;
			tmp = (tmp * 10) + (b[i] & 0x0F);
			ret = ret* 100 +  tmp;
		}
		return ret;
	}
	public static int toIntR(byte[] b, int s, int n) {
		int ret = 0;

		for (int i = s; (i >= 0 && n > 0); --i, --n) {
			ret <<= 8;
			ret |= b[i] & 0xFF;
		}
		return ret;
	}

	public static int toInt(byte... b) {
		int ret = 0;
		for (final byte a : b) {
			ret <<= 8;
			ret |= a & 0xFF;
		}
		return ret;
	}

	public static String toHexString(byte[] d, int s, int n) {
		final char[] ret = new char[n * 2];
		final int e = s + n;

		int x = 0;
		for (int i = s; i < e; ++i) {
			final byte v = d[i];
			ret[x++] = HEX[0x0F & (v >> 4)];
			ret[x++] = HEX[0x0F & v];
		}
		return new String(ret);
	}

	public static String toHexStringR(byte[] d, int s, int n) {
		final char[] ret = new char[n * 2];

		int x = 0;
		for (int i = s + n - 1; i >= s; --i) {
			final byte v = d[i];
			ret[x++] = HEX[0x0F & (v >> 4)];
			ret[x++] = HEX[0x0F & v];
		}
		return new String(ret);
	}

	public static int parseInt(String txt, int radix, int def) {
		int ret;
		try {
			ret = Integer.valueOf(txt, radix);
		} catch (Exception e) {
			ret = def;
		}

		return ret;
	}
	
	public static String toAmountString(float value) {
		return String.format("%.2f", value);
	}
	/**
	 * 
	 * @param tag
	 * @param tlvList
	 * @param offset
	 * @param length
	 * @return the offset of the value in buffer,not offset of the tag.
	 */
	public static short findValueOffByTag(short tag, byte[] tlvList,
			short offset, short length) {
		short i = offset;
		length += offset;

		while (i < length) {
			// tag
			short tagTemp = (short) (tlvList[i] & 0x00FF);
			if ((short) (tagTemp & 0x001F) == 0x001F) {
				i++;
				tagTemp <<= 8;
				tagTemp |= (short) (tlvList[i] & 0x00FF);
			}
			i++;

			// length
			if (tlvList[i] == (byte) 0x81) {
				i++;
			}
			i++;

			// value
			if (tag == tagTemp) {
				return i;
			}

			i += (tlvList[(short) (i - 1)] & 0x00FF);
		}
		return TAG_NOT_FOUND;
	}
	
	public static short findandCopyValueByTag(short tag, byte[] tlvList,
			short offset, short length, byte[] destbuflv) {
		short toffset = 0;
		toffset = findValueOffByTag(tag, tlvList, offset, length);
		if (toffset == TAG_NOT_FOUND)
			return TAG_NOT_FOUND;
		else {
			destbuflv[0] = tlvList[(short) (toffset - 1)];
			System.arraycopy(tlvList, toffset, destbuflv, (short) 0x0001,
					(short) destbuflv[0]);
			return toffset;
		}
	}
	
	public static short GetTagInDOL(byte[] pDOL, short index) {
		short toffset = 1;
		short ttag = 0;
		short pDOLlength = (short) pDOL[0];

		if (index < 1)
			return (short) -1;
		while (index > 0) {
			if (toffset > pDOLlength)
				return (short) -1;
			ttag = (short) (pDOL[toffset++] & 0x00FF);
			if (((short) (ttag & 0x001F)) == ((short) 0x001F)) {
				ttag <<= 8;
				ttag |= (short) (pDOL[toffset++] & 0x00FF);
			}

			if (toffset > pDOLlength)
				return (short) -1;

			if ((short) pDOL[toffset] > (short) 0x0080) {
				toffset += (short) ((short) pDOL[toffset] - (short) 0x0080);
			} else {
				toffset += 1;
			}
			index--;
		}
		return ttag;
	}
	
	public static short arrayCopy(byte[] src, short srcOff, byte[] dest,short destOff, short length) {
		System.arraycopy(src, srcOff, dest, destOff, length);
		return (short) (destOff+length);
	}
}
