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
			SmsObjectS100 sms100 = new SmsObjectS100();
			sms100.setSucc(0);
			sms100.setMsg("你还没有登录");
			session.write(sms100);
			session.close(false);
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
        	log.error(">>" + cause.getMessage());
        	if (cause.getMessage().indexOf("Connection reset by peer") != -1) {
        		log.error("exceptionCaught close session:" + session.getId());
        		ExecService.getInstance().sessionClosed(session);
        		session.close(false);
        	}
        }
    }
	
    public void sessionClosed(IoSession session) throws Exception {
    	log.info("sessionClosed:" + session.getId());
    	if (session.getAttribute("execUser") != null && session.getAttribute("execUser") instanceof ExecUser) {
    		ExecUser user = (ExecUser)session.getAttribute("execUser");
    		log.info(user.getName() + "断开链接");
    	}
    	ExecService.getInstance().sessionClosed(session);
    }
}
