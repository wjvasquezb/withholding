package ve.net.dcs.process;

import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;

import ve.net.dcs.model.MLVEVoucherWithholding;

public class VWT_ProcessWithholding extends SvrProcess {
	private int p_Record_ID = 0;
	private String docAction = "";

	public VWT_ProcessWithholding() {
	}

	@Override
	protected void prepare() {
		ProcessInfoParameter[] para = getParameter();
		for (ProcessInfoParameter p : para) {
			String name = p.getParameterName();
			if (name == null)
				;
			else if (name.equals("DocAction"))
				docAction = p.getParameter().toString();
			else
				log.severe("Unknown Parameter: " + name);
		}
		p_Record_ID = getRecord_ID();

	}

	@Override
	protected String doIt() throws Exception {
		MLVEVoucherWithholding voucher = new MLVEVoucherWithholding(getCtx(), p_Record_ID, get_TrxName());

		if (docAction.equals("CO"))
			return voucher.completeIt();
		else if (docAction.equals("VO"))
			return voucher.voidIt();
		else if (docAction.equals("RE"))
			return voucher.reActiveIt();
		else
			return null;
	}

}
