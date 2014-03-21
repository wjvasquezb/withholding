package ve.net.dcs.component;

import java.util.ArrayList;
import java.util.List;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.I_C_Invoice;
import org.compiere.model.MBPartner;
import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.model.MSequence;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.model.X_C_BPartner;
import org.compiere.process.DocAction;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.globalqss.model.MLCOInvoiceWithholding;
import org.globalqss.model.X_LCO_InvoiceWithholding;
import org.osgi.service.event.Event;

import ve.net.dcs.model.MLVEVoucherWithholding;

public class VWTModelValidator extends AbstractEventHandler {

	private static CLogger log = CLogger.getCLogger(VWTModelValidator.class);

	@Override
	protected void initialize() {
		registerTableEvent(IEventTopics.DOC_AFTER_COMPLETE, I_C_Invoice.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_COMPLETE, I_C_Invoice.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_VOID, I_C_Invoice.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_REACTIVATE, I_C_Invoice.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_REVERSEACCRUAL, I_C_Invoice.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_REVERSECORRECT, I_C_Invoice.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_CHANGE, I_C_BPartner.Table_Name);
	}

	@Override
	protected void doHandleEvent(Event event) {

		PO po = getPO(event);
		String type = event.getTopic();
		log.info(po.get_TableName() + " Type: " + type);

		if (po.get_TableName().equals(I_C_BPartner.Table_Name) && type.equals(IEventTopics.PO_BEFORE_CHANGE)) {
			X_C_BPartner partner = (X_C_BPartner) po;
			if (partner.getTaxID().equalsIgnoreCase((String)partner.get_ValueOld("TaxId"))){
				if (!((X_C_BPartner) po).getTaxID().matches("[0-9]+"))
					throw new RuntimeException("Caracteres no válidos en número de identificación");	
			}
		} else if (po.get_TableName().equals(I_C_Invoice.Table_Name) && type.equals(IEventTopics.DOC_AFTER_COMPLETE)) {

			MInvoice invoice = (MInvoice) po;
			
			if (!invoice.isReversal()){
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
						voucher.setDateTrx(invoice.getDateAcct());
						voucher.setC_BPartner_ID(invoice.getC_BPartner_ID());
						voucher.setLCO_WithholdingType_ID(LCO_WithholdingType_ID);
						voucher.setC_Invoice_ID(iw.getC_Invoice_ID());
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
			

		} else if (po.get_TableName().equals(I_C_Invoice.Table_Name) && (type.equals(IEventTopics.DOC_BEFORE_VOID) || type.equals(IEventTopics.DOC_BEFORE_REACTIVATE) || type.equals(IEventTopics.DOC_BEFORE_REVERSEACCRUAL) || type.equals(IEventTopics.DOC_BEFORE_REVERSECORRECT))) {
			MInvoice invoice = (MInvoice) po;
			
			List<MLCOInvoiceWithholding> list = new Query(invoice.getCtx(), MLCOInvoiceWithholding.Table_Name, " C_Invoice_ID = ? ", invoice.get_TrxName()).setParameters(invoice.getC_Invoice_ID()).setOrderBy(MLCOInvoiceWithholding.COLUMNNAME_C_Invoice_ID).list();
			
			if (list.size() > 0){
				for (MLCOInvoiceWithholding mlcoInvoiceWithholding : list) {
					if (mlcoInvoiceWithholding.get_ValueAsInt("LVE_VoucherWithholding_ID") > 0){
						MLVEVoucherWithholding vw = new MLVEVoucherWithholding(invoice.getCtx(), mlcoInvoiceWithholding.get_ValueAsInt("LVE_VoucherWithholding_ID"), invoice.get_TrxName());
						if (vw.getDocStatus().equalsIgnoreCase(DocAction.STATUS_Completed) || vw.getDocStatus().equalsIgnoreCase(DocAction.STATUS_Drafted)){
							throw new AdempiereException("RETENCIÓN. "+vw.getWithholdingNo()+", Debe Anular primero la Retención Asociada");
						}
					}else{
						mlcoInvoiceWithholding.deleteEx(true);
					}
				}
			}
		}
		else if (po.get_TableName().equals(I_C_Invoice.Table_Name) && type.equals(IEventTopics.DOC_BEFORE_COMPLETE)) {

			String msgExistCN = Msg.translate(Env.getCtx(), "AlreadyExists") + ": " + Msg.getElement(Env.getCtx(), "LVE_controlNumber");
			String msgExistINo = Msg.translate(Env.getCtx(), "AlreadyExists") + ": " + Msg.getElement(Env.getCtx(), "LVE_POInvoiceNo");
			String msgMandataryCN = Msg.translate(Env.getCtx(), "FillMandatory") + ": " + Msg.getElement(Env.getCtx(), "LVE_controlNumber");
			String msgMandataryINo = Msg.translate(Env.getCtx(), "FillMandatory") + ": " + Msg.getElement(Env.getCtx(), "LVE_POInvoiceNo");
			String msgSeqNotFound = Msg.translate(Env.getCtx(), "SequenceDocNotFound") + " " + Msg.getElement(Env.getCtx(), "LVE_ControlNoSequence_ID");

			String where = "AD_Org_ID=? AND C_BPartner_ID=? AND C_Invoice_ID!=? AND IsSOTrx=? AND DocStatus IN ('CO','CL') ";
			MInvoice invoice = (MInvoice) po;
			MDocType docType = (MDocType) invoice.getC_DocType();

			if (invoice.getReversal_ID() == 0)
				if (invoice.isSOTrx()) {
					String controlSequence = null;
					if (invoice.get_Value("LVE_controlNumber") == null) {
						if (docType.get_Value("LVE_ControlNoSequence_ID") == null && docType.get_ValueAsBoolean("isControlNoDocument")) {
							throw new AdempiereException(msgSeqNotFound);
						}

						MSequence seq = new MSequence(Env.getCtx(), (int) docType.get_Value("LVE_ControlNoSequence_ID"), po.get_TrxName());
						controlSequence = MSequence.getDocumentNoFromSeq(seq, po.get_TrxName(), invoice);

						Query query = new Query(Env.getCtx(), MInvoice.Table_Name, where + "AND LVE_controlNumber=?", po.get_TrxName());

						while (query.setParameters(invoice.getAD_Org_ID(), invoice.getC_BPartner_ID(), invoice.get_ID(), invoice.isSOTrx(), controlSequence).count() > 0) {
							seq.setCurrentNext(seq.getCurrentNext() + 1);
							seq.saveEx();
							controlSequence = MSequence.getDocumentNoFromSeq(seq, po.get_TrxName(), invoice);
						}
						invoice.set_ValueOfColumn("LVE_controlNumber", controlSequence);
						invoice.saveEx();
					} else {
						boolean existCN = new Query(Env.getCtx(), MInvoice.Table_Name, where + "AND LVE_controlNumber=?", po.get_TrxName()).setParameters(invoice.getAD_Org_ID(), invoice.getC_BPartner_ID(), invoice.get_ID(), invoice.isSOTrx(), invoice.get_Value("LVE_controlNumber")).count() > 0;
						if (existCN) {
							throw new AdempiereException(msgExistCN);
						}
					}
				} else {
					if (invoice.get_Value("LVE_controlNumber") == null) {
						throw new AdempiereException(msgMandataryCN);
					} else if (invoice.get_Value("LVE_POInvoiceNo") == null) {
						throw new AdempiereException(msgMandataryINo);
					} else {
						boolean existCN = new Query(Env.getCtx(), MInvoice.Table_Name, where + "AND LVE_controlNumber=? AND LVE_POInvoiceNo=?", po.get_TrxName()).setParameters(invoice.getAD_Org_ID(), invoice.getC_BPartner_ID(), invoice.get_ID(), invoice.isSOTrx(), invoice.get_Value("LVE_controlNumber"), invoice.get_Value("LVE_POInvoiceNo")).count() > 0;
						if (existCN) {
							throw new AdempiereException(msgExistCN);
						}
					}
				}
		}
	}
}