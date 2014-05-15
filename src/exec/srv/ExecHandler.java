package exec.srv;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import exec.common.ISmsObject;
import exec.proto.SmsObjectS100;

public class ExecHandler extends IoHandlerAdapter {

	private final static Logger log = LoggerFactory.getLogger(ExecHandler.class);

	public void sessionOpened(IoSession session) throws Exception {
    }
	
	@Override
	public void messageReceived(IoSession session, Object message) throws Exception {
		ISmsObject sms = (ISmsObject)message;
		int proto = sms.getProto();
		log.info("session[" + session.getId() + "]received proto [" + proto + "]");
		if (proto != 100 && session.getAttribute("verification") == null) {
			SmsObjectS100 sms200 = new SmsObjectS100();
			session.write(sms200);
			return;
		}
		ISmsObject returnVal = ExecService.getInstance().handle(sms);
		if (returnVal != null) {
			session.write(returnVal);
		}
	}
	
	public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
		log.info("exceptionCaught:" + session.getId());
        if (log.isWarnEnabled()) {
        	log.warn("EXCEPTION, please implement " + getClass().getName()
                    + ".exceptionCaught() for proper handling:", cause);
        }
    }
	
    public void sessionClosed(IoSession session) throws Exception {
    	ExecService.getInstance().sessionClosed(session);
    }
}
