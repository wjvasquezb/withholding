package ve.net.dcs.component;

import java.util.ArrayList;
import java.util.List;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.I_C_Invoice;
import org.compiere.model.MInvoice;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.model.X_C_BPartner;
import org.compiere.util.CLogger;
import org.globalqss.model.MLCOInvoiceWithholding;
import org.globalqss.model.X_LCO_InvoiceWithholding;
import org.osgi.service.event.Event;

import ve.net.dcs.model.MLVEVoucherWithholding;

public class VWTModelValidator extends AbstractEventHandler {

	private static CLogger log = CLogger.getCLogger(VWTModelValidator.class);

	@Override
	protected void initialize() {
		registerTableEvent(IEventTopics.DOC_AFTER_COMPLETE, I_C_Invoice.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_CHANGE, I_C_BPartner.Table_Name);
	}

	@Override
	protected void doHandleEvent(Event event) {

		PO po = getPO(event);
		String type = event.getTopic();
		log.info(po.get_TableName() + " Type: " + type);

//		if (po.get_TableName().equals(I_C_BPartner.Table_Name) && type.equals(IEventTopics.PO_BEFORE_CHANGE)) {
//			if (!((X_C_BPartner) po).getTaxID().matches("[0-9]+"))
//				throw new RuntimeException("Caracteres no válidos en número de identificación");
//		} else 
		if (po.get_TableName().equals(I_C_Invoice.Table_Name) && type.equals(IEventTopics.DOC_AFTER_COMPLETE)) {

			MInvoice invoice = (MInvoice) po;
			String sqlwhere = " C_Invoice_ID = ? AND LVE_VoucherWithholding_ID IS NULL";
			List<MLCOInvoiceWithholding> invoiceW = new Query(po.getCtx(), X_LCO_InvoiceWithholding.Table_Name, sqlwhere, po.get_TrxName()).setOnlyActiveRecords(true).setParameters(invoice.get_ID()).setOrderBy("LCO_WithholdingType_ID").list();

			int LCO_WithholdingType_ID = 0;
			MLVEVoucherWithholding voucher = null;
			List<MLVEVoucherWithholding> listVoucher = new ArrayList<MLVEVoucherWithholding>();

			for (MLCOInvoiceWithholding iw : invoiceW) {
				if (LCO_WithholdingType_ID != iw.getLCO_WithholdingType_ID()) {
					LCO_WithholdingType_ID = iw.getLCO_WithholdingType_ID();
					voucher = new MLVEVoucherWithholding(po.getCtx(), 0, po.get_TrxName());
					voucher.setAD_Org_ID(po.getAD_Org_ID());
					voucher.set_ValueOfColumn("AD_Client_ID", po.getAD_Client_ID());
					voucher.setDateTrx(invoice.getDateInvoiced());
					voucher.setC_BPartner_ID(invoice.getC_BPartner_ID());
					voucher.setLCO_WithholdingType_ID(LCO_WithholdingType_ID);
					voucher.saveEx();
					listVoucher.add(voucher);
				}

				iw.set_ValueOfColumn("LVE_VoucherWithholding_ID", voucher.get_ID());
				iw.saveEx();
			}

			for (MLVEVoucherWithholding v : listVoucher) {
				v.completeIt();
				v.saveEx();
			}

		}

	}
}