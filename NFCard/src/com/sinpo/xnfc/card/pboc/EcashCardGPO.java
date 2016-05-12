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

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;

import android.content.res.Resources;

import com.sinpo.xnfc.R;
import com.sinpo.xnfc.Util;
import com.sinpo.xnfc.tech.FeliCa.Response;
import com.sinpo.xnfc.tech.Iso7816;

final class EcashCardGPO extends PbocCard {
	private final static byte[] PPSE = { 
		(byte) '2',(byte) 'P', (byte) 'A', (byte) 'Y',(byte) '.', 
		(byte) 'S', (byte) 'Y', (byte) 'S', (byte) '.',
		(byte) 'D', (byte) 'D', (byte) 'F',(byte) '0', (byte) '1'  };

	private EcashCardGPO(Iso7816.Tag tag, Resources res) {
		super(tag);
		name = res.getString(R.string.name_pbocecash);
	}
	

	@SuppressWarnings("unchecked")
	final static EcashCardGPO load(Iso7816.Tag tag, Resources res) {
		byte[] tbyte = null;
		int toff = 0;
		short fcioff = 0;
		byte[] PDOL = null;
		/*--------------------------------------------------------------*/
		// select PPSE (2PAY.SYS.DDF01)
		/*--------------------------------------------------------------*/
		Iso7816.Response ppseres = tag.selectByName(PPSE);
		if (ppseres.isOkey()) {
			tbyte = ppseres.getBytes();
			toff = 0;
			byte[] DFAID = null;
			
			if((byte)0x6F == tbyte[toff++]){
				if((byte)0x81 == tbyte[toff++]){
					toff++;
				}
				short FCIoff = Util.findValueOffByTag((short) 0xA5, 
						tbyte,(short)toff,(short)tbyte[toff-1]);
				if(FCIoff < 0)
					return  null;
				
				short FCIdefoff = Util.findValueOffByTag((short) 0xBF0C, 
						tbyte,(short)FCIoff,(short)tbyte[FCIoff-1]);
				if(FCIoff < 0)
					return  null;
				
				short Contentoff = Util.findValueOffByTag((short) 0x61, 
						tbyte,(short)FCIdefoff,(short)tbyte[FCIdefoff-1]);
				if(Contentoff < 0)
					return  null;
				
				short DFAIDoff = Util.findValueOffByTag((short) 0x4F, 
						tbyte,(short)Contentoff,(short)tbyte[Contentoff-1]);
				if(DFAIDoff < 0)
					return  null;
				
				short APPlaboff = Util.findValueOffByTag((short) 0x50, 
						tbyte,(short)Contentoff,(short)tbyte[Contentoff-1]);
				if(APPlaboff < 0)
					return  null;
				
				DFAID = new byte[tbyte[DFAIDoff - 1]];
				System.arraycopy(tbyte, DFAIDoff, DFAID, 0, DFAID.length);
				
			}else{
				return null;
			}
			
			
			Iso7816.Response INFO, CASH,ATC,res_GPO;

			/*--------------------------------------------------------------*/
			// select Main Application
			/*--------------------------------------------------------------*/
			Iso7816.Response selectdffci = tag.selectByName(DFAID);
			if (selectdffci.isOkey()) {
				tbyte = selectdffci.getBytes();
				toff = 0;
				byte logsfi = 0;
				if((byte)0x6F == tbyte[toff++]){
					if((byte)0x81 == tbyte[toff++]){
						toff++;
					}
					toff = Util.findValueOffByTag((short) 0xA5, 
							tbyte,(short)toff,(short)tbyte[toff-1]);
					if(toff < 0)
						return  null;
					int toffA5 = toff;
					
					//PDOL
					toff = Util.findValueOffByTag((short) 0x9F38, 
							tbyte,(short)toffA5,(short)tbyte[toff-1]);
					if(toff < 0)
						return  null;
					PDOL = new byte[tbyte[toff - 1]+1];
					PDOL[0] = tbyte[toff - 1];
					System.arraycopy(tbyte, toff, PDOL, 1, PDOL[0]);
					
					
					toff = Util.findValueOffByTag((short) 0xBF0C, 
							tbyte,(short)toffA5,(short)tbyte[toffA5-1]);
					if(toff < 0)
						return  null;
					
					toff = Util.findValueOffByTag((short) 0x9F4D, 
							tbyte,(short)toff,(short)tbyte[toff-1]);
					if(toff < 0)
						return  null;

					logsfi = tbyte[toff];
				}
				
				toff = PbocGPO(tag,PDOL);

				
				/*--------------------------------------------------------------*/
				// read card info file, binary (21)
				/*--------------------------------------------------------------*/
				INFO = tag.readRecord(2,1);

				/*--------------------------------------------------------------*/
				// read balance
				/*--------------------------------------------------------------*/
				CASH = tag.getPBOCBalance();
				
				// read ATC
				/*--------------------------------------------------------------*/
				ATC = tag.getData(0x9F36);
				
				// read 日志格式
				/*--------------------------------------------------------------*/
				byte[] logdol = tag.getData(0x9F4F).getBytes();
			
				
				/*--------------------------------------------------------------*/
				// read log file,
				/*--------------------------------------------------------------*/
				ArrayList<byte[]> LOG = readLog(tag, logsfi);
				
				
				
				
				/*--------------------------------------------------------------*/
				// build result string
				/*--------------------------------------------------------------*/
				final EcashCardGPO ret = new EcashCardGPO(tag, res);
				ret.parseInfo(INFO);
				ret.parseBalance(CASH);
				ret.parseLog(logdol,LOG);
				ret.parseData("ATC",ATC);

				return ret;
			}
		}

		return null;
	}
	
	/**
	 * 解析PBOC卡牌你信息
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
		if((byte)0x70 == d[0]){
			// PAN
			short serloff = Util.findValueOffByTag((short) 0x5A, d,(short)2,(short)d.length);
			if(serloff > 0){
				serl = Util.toHexString(d, serloff, d[serloff-1]);
			}
			if(("F").equals(serl.substring(serl.length() - 1))){
				serl = 	serl.substring(0,serl.length() - 1);
			}
		}else{
			serl ="**ERR**";
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
	

	
	protected void parseLog(byte[] logDOL,ArrayList<byte[]>... logs) {
		final StringBuilder r = new StringBuilder();
		
		int lognum = 0;// 有效日志数
		int logdateoffset = 0;// 交易日期在日志记录中的偏移
		int logtypeoffset = 0;// 交易类型在日志记录中的偏移
		int logtimeoffset = 0;// 交易时间在日志记录中的偏移
		int logmoneyoffset = 0;// 授权金额在日志记录中的偏移
		int lognameoffset = 0;// 商户名称在日志记录中的偏移
		// 查找各个tag在日志记录中的位置
		int toffset = 3;
		//tag偏移 
		int tagoff = 0;
		short tagTemp = 0;
		// 获取 交易日期 和交易时间在记录中的偏移，其他偏移也一并获取
		while (toffset < (short) (logDOL[2] + 3)) {
			// tag
			tagTemp = (short) (logDOL[toffset] & 0x00FF);
			if ((short) (tagTemp & 0x001F) == 0x001F) {
				toffset++;
				tagTemp <<= 8;
				tagTemp |= (short) (logDOL[toffset] & 0x00FF);
			}
			toffset++;

			// length
			if (logDOL[toffset] == (byte) 0x81) {
				toffset++;
			}
			// value==空

			if (tagTemp == (short) 0x9C) {
				logtypeoffset = tagoff;// 交易类型偏移
			} else if (tagTemp == (short) 0x9A) {
				logdateoffset = tagoff;// 交易日期偏移
			} else if (tagTemp == (short) 0x9F21) {
				logtimeoffset = tagoff;// 交易时间偏移
			} else if (tagTemp == (short) 0x9F02) {
				logmoneyoffset = tagoff;// 授权金额偏移
			} else if (tagTemp == (short) 0x9F4E) {
				lognameoffset = tagoff;// 商户名称偏移
			}
			tagoff += logDOL[toffset];
			toffset++;
		}

		for (final ArrayList<byte[]> log : logs) {
			if (log == null)
				continue;

			if (r.length() > 0)
				r.append("<br />--------------");

			for (final byte[] v : log) {
				final int cash = Util.BCDtoInt(v, logmoneyoffset+2, 4);
				if (cash > 0) {
					r.append("<br />").append(
							String.format("20%02X.%02X.%02X,%02X:%02X:%02X,",
									v[logdateoffset], v[logdateoffset+1], v[logdateoffset+2], 
									v[logtimeoffset], v[logtimeoffset+1],v[logtimeoffset+2]));

					final char t = (v[logtypeoffset] == (byte)0x60 || v[logtypeoffset] == (byte)0x63) ? '+'
							: '-';

					r.append(t).append(Util.toAmountString(cash / 100.0f));

//					final int over = Util.toInt(v, 2, 3);
//					if (over > 0)
//						r.append(" [o:")
//								.append(Util.toAmountString(over / 100.0f))
//								.append(']');
//
//					r.append(" [").append(Util.toHexString(v, 10, 6))
//							.append(']');
				}
			}
		}

		this.log = r.toString();
	}
	
	protected static ArrayList<byte[]> readLog(Iso7816.Tag tag, int sfi) {
		final ArrayList<byte[]> ret = new ArrayList<byte[]>(MAX_LOG);
		
		for (int i = 1; i <= MAX_LOG; ++i) {
			final Iso7816.Response tres = tag.readRecord(sfi, i);
			if(tres.isOkey()){
				ret.add(tres.getBytes());
			}else
				break;
		}
		return ret;
	}
	
	/**
	 * PBOC GPO
	 * 
	 * @return SW
	 */
	public static int PbocGPO(Iso7816.Tag tag,byte[] PDOL) {
		short ttag = 0;
		short tcmdoffset = 0;
		short tresoffset = 0;
		boolean tagaddflag = false;
		final byte[] CmdBuf=new byte[0x40];
		// 文件定位器,
		final byte[] AFL = new byte[0x41];
		// 应用交互特征
		final byte[] AIP = new byte[2];// AIP
		
		// 固定长度TAG,Value
		// PDOL = 9F6604 9F0206 9F0306 9F1A02 9505 5F2A02 9A03 9C01 9F3704
		final byte[] tag9F66value = { (byte) 0xBE, (byte) 0x00, (byte) 0x00,(byte) 0x00 };// 终端属相
		final byte[] tag9F02value =	{ (byte) 0x00, (byte) 0x00, (byte) 0x00,(byte) 0x00, (byte) 0x00, (byte) 0x01 };// 授权金额
		final byte[] tag9F03value =	{ (byte) 0x00, (byte) 0x00, (byte) 0x00,(byte) 0x00, (byte) 0x00, (byte) 0x00 };;// 其他金额
		final byte[] tag9F1Avalue = { (byte) 0x01, (byte) 0x56 };// 终端国家代码
		final byte[] tag95value = { (byte) 0x00, (byte) 0x00, (byte) 0x00,(byte) 0x00, (byte) 0x00 };// 终端验证结果,TVR
		final byte[] tag5F2Avalue = { (byte) 0x01, (byte) 0x56 };// 交易货币代码
		final byte[] tag9Avalue = { (byte) 0x16,(byte) 0x01,(byte) 0x01};// 交易日期
		final byte[] tag9Cvalue = { (byte) 0x00 };// 交易类型,quickpass
		final byte[] tag9F37value = { (byte) 0x11,(byte) 0x22,(byte) 0x33,(byte) 0x44 };// 不可预知数
		final byte[] tagDF60value = { (byte) 0x00 };// CAPP 交易指示位,00--终端不支持扩展应用

		

		CmdBuf[tcmdoffset++] = (byte) 0x80;
		CmdBuf[tcmdoffset++] = (byte) 0xA8;
		CmdBuf[tcmdoffset++] = (byte) 0x00;
		CmdBuf[tcmdoffset++] = (byte) 0x00;
		CmdBuf[tcmdoffset++] = (byte) 0x02;// LC暂设为0
		CmdBuf[tcmdoffset++] = (byte) 0x83;
		CmdBuf[tcmdoffset++] = (byte) 0x00;// 暂设为0

		// 添加PDOL数据
		for (short i = 1;; i++) {
			ttag = Util.GetTagInDOL(PDOL, i);
			if (ttag == (short) -1)
				break;

			// 9F6604 9F0206 9F0306 9F1A02 9505 5F2A02 9A03 9C01 9F3704 DF6001
			if ((short) 0x9F66 == ttag) {
				tcmdoffset = Util.arrayCopy(tag9F66value, (short) 0x0000,
						CmdBuf, tcmdoffset, (short) tag9F66value.length);
				continue;
			}
			if ((short) 0x9F02 == ttag) {
				tcmdoffset = Util.arrayCopy(tag9F02value, (short) 0x0000,
						CmdBuf, tcmdoffset, (short) tag9F02value.length);
				continue;
			}
			if ((short) 0x9F03 == ttag) {
				tcmdoffset = Util.arrayCopy(tag9F03value, (short) 0x0000,
						CmdBuf, tcmdoffset, (short) tag9F03value.length);
				continue;
			}
			if ((short) 0x9F1A == ttag) {
				tcmdoffset = Util.arrayCopy(tag9F1Avalue, (short) 0x0000,
						CmdBuf, tcmdoffset, (short) tag9F1Avalue.length);
				continue;
			}
			if ((short) 0x95 == ttag) {
				tcmdoffset = Util.arrayCopy(tag95value, (short) 0x0000, CmdBuf,
						tcmdoffset, (short) tag95value.length);
				continue;
			}
			if ((short) 0x5F2A == ttag) {
				tcmdoffset = Util.arrayCopy(tag5F2Avalue, (short) 0x0000,
						CmdBuf, tcmdoffset, (short) tag5F2Avalue.length);
				continue;
			}
			if ((short) 0x9A == ttag) {
				tcmdoffset = Util.arrayCopy(tag9Avalue, (short) 0x0000, CmdBuf,
						tcmdoffset, (short) tag9Avalue.length);
				continue;
			}
			if ((short) 0x9C == ttag) {
				tcmdoffset = Util.arrayCopy(tag9Cvalue, (short) 0x0000, CmdBuf,
						tcmdoffset, (short) tag9Cvalue.length);
				continue;
			}
			if ((short) 0x9F37 == ttag) {
				tcmdoffset = Util.arrayCopy(tag9F37value, (short) 0x0000,
						CmdBuf, tcmdoffset, (short) tag9F37value.length);
				continue;
			}
			if ((short) 0xDF60 == ttag) {
				tcmdoffset = Util.arrayCopy(tagDF60value, (short) 0x0000,
						CmdBuf, tcmdoffset, (short) tagDF60value.length);
				continue;
			}
//			if ((short) 0x9F7A == ttag) {
//				tcmdoffset = Util.arrayCopy(tag9F7Avalue, (short) 0x0000,
//						CmdBuf, tcmdoffset, (short) tag9F7Avalue.length);
//				continue;
//			}
//			if ((short) 0x9F7B == ttag) {
//				tcmdoffset = Util.arrayCopy(tag9F7Bvalue, (short) 0x0000,
//						CmdBuf, tcmdoffset, (short) tag9F7Bvalue.length);
//				continue;
//			}
			return -1;// PDOL的TAG没有对应数据
		}
		CmdBuf[4] = (byte) (tcmdoffset - 5);
		CmdBuf[6] = (byte) (tcmdoffset - 7);
		Iso7816.Response res_GPO = tag.sendCmd(CmdBuf);
		byte[] ResBuf = res_GPO.getBytes();
		
		// GPO指令返回值，80+L+AIP+AFL
		tresoffset = 0;
		if ((byte) 0x80 == ResBuf[tresoffset++]) {
			// AIP
			System.arraycopy(ResBuf, tresoffset + 1, AIP,0, AIP.length);
			// AFL
			AFL[0] = (byte) (ResBuf[tresoffset] - 2);
			System.arraycopy(ResBuf, tresoffset + 3, AFL,1, AFL[0]);
		} else if ((byte) 0x77 == ResBuf[tresoffset++]) {
			// 77莫班委，暂时不做处理 20150615
		}

		return 0;
	}
}
