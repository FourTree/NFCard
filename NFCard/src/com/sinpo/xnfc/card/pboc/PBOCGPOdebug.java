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

package com.sinpo.xnfc.card.pboc;

import java.util.ArrayList;

import android.content.res.Resources;

import com.sinpo.xnfc.R;
import com.sinpo.xnfc.Util;
import com.sinpo.xnfc.tech.Iso7816;

final class PBOCGPOdebug extends PbocCard {
	private final static byte[] DFN_SRV = { (byte) 0xA0, (byte) 0x00,
			(byte) 0x00, (byte) 0x03, (byte) 0x33, (byte) 0x01, (byte) 0x01,
			(byte) 0x06, (byte) 0x51, (byte) 0x33, (byte) 0x02, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x17 };

	private final static byte[] ISD_AID = { (byte) 0xA0, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x00,
			(byte) 0x00 };
	// 80a8000024832244000000000000000100000000000000015600000000000156160214001122334400
	private final static byte[] CMD_GPO = { (byte) 0x80, (byte) 0xa8,
			(byte) 0x00, (byte) 0x00, (byte) 0x24, (byte) 0x83, (byte) 0x22,
			(byte) 0x44, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x01, (byte) 0x56, (byte) 0x00, (byte) 0x00,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x56,
			(byte) 0x16, (byte) 0x02, (byte) 0x14, (byte) 0x00, (byte) 0x11,
			(byte) 0x22, (byte) 0x33, (byte) 0x44, (byte) 0x00 };

	private PBOCGPOdebug(Iso7816.Tag tag, Resources res) {
		super(tag);
		name = res.getString(R.string.name_pboctest);
	}

	@SuppressWarnings("unchecked")
	final static PBOCGPOdebug load(Iso7816.Tag tag, Resources res) {

		/*--------------------------------------------------------------*/
		// select PSF (1PAY.SYS.DDF01)
		/*--------------------------------------------------------------*/
		if (tag.selectByName(ISD_AID).isOkey()) {

			Iso7816.Response INFO, CASH, ATC,res_GPO,res_Select;

			/*--------------------------------------------------------------*/
			// select Main Application
			/*--------------------------------------------------------------*/
			if (tag.selectByName(DFN_SRV).isOkey()) {

				/*--------------------------------------------------------------*/
				// read card info file, binary (21)
				/*--------------------------------------------------------------*/
				INFO = tag.readRecord(2, 1);

				/*--------------------------------------------------------------*/
				// read balance
				/*--------------------------------------------------------------*/
				CASH = tag.getPBOCBalance();
				// read ATC
				/*--------------------------------------------------------------*/
				ATC = tag.getData(0x9F36);
				/*--------------------------------------------------------------*/

				for(int cnt = 0;cnt <0xFFF;cnt ++){
					res_Select = tag.selectByName(DFN_SRV);
					if(!res_Select.isOkey()){
						break;
					}
					res_GPO = tag.sendCmd(CMD_GPO);
					System.out.println(cnt+"+"+res_GPO);
					if(!res_GPO.isOkey()){
						res_Select = tag.selectByName(ISD_AID);
						System.out.println(res_Select);

						res_Select = tag.selectByName(DFN_SRV);
						System.out.println(res_Select);

						ATC = tag.getData(0x9F36);
						System.out.println(ATC);

						break;
					}
				}
				
				// read log file, record (24)
				/*--------------------------------------------------------------*/
				// ArrayList<byte[]> LOG = readLog(tag, SFI_LOG);

				/*--------------------------------------------------------------*/
				// build result string
				/*--------------------------------------------------------------*/
				final PBOCGPOdebug ret = new PBOCGPOdebug(tag, res);
				ret.parseBalance(CASH);
				ret.parseInfo(INFO);
				ret.parseData("ATC",ATC);
				// ret.parseLog(LOG);

				return ret;
			}
		}

		return null;
	}

	/**
	 * 解析PBOC卡牌你信息
	 * 
	 * @param data
	 * @param dec
	 * @param bigEndian
	 */
	protected void parseInfo(Iso7816.Response data) {
		if (!data.isOkey() || data.size() < 30) {
			serl = version = date = count = null;
			return;
		}

		final byte[] d = data.getBytes();
		if ((byte) 0x70 == d[0]) {
			// PAN
			short serloff = Util.findValueOffByTag((short) 0x5A, d, (short) 2,
					(short) d.length);
			if (serloff > 0) {
				serl = Util.toHexString(d, serloff, d[serloff - 1]);
			}
		} else {
			serl = "**ERR**";
		}

		version = null;
		date = null;
		count = null;
	}

	protected void parseBalance(Iso7816.Response data) {
		if (!data.isOkey() || data.size() < 4) {
			cash = null;
			return;
		}

		int n = Util.BCDtoInt(data.getBytes(), 5, 4);
		cash = Util.toAmountString(n / 100.0f);
	}
	
}
