package exec.srv;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import exec.proto.SmsObject;



public class MyIoHandler extends IoHandlerAdapter {

	private final static Logger log = LoggerFactory.getLogger(MyIoHandler.class);

	@Override
	public void messageReceived(IoSession session, Object message) throws Exception {
		SmsObject sms = (SmsObject)message;
		int proto = sms.getProto();
		String str = sms.getContent();
		log.info("============sessionid:" + session.getId());
		log.info("The message received is [" + str + "] proto is[" + proto + "]");
		sms.setContent("回复:" + sms.getContent());
		sms.setParam("回复:" + sms.getParam());
		session.write(sms);
		if (str.endsWith("quit")) {
			session.close(true);
			return;
		}
	}
	
	public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
		log.info("============exceptionCaught:" + session.getId());
        if (log.isWarnEnabled()) {
        	log.warn("EXCEPTION, please implement " + getClass().getName()
                    + ".exceptionCaught() for proper handling:", cause);
        }
    }
    public void sessionClosed(IoSession session) throws Exception {
    	log.info("============sessionClosed:" + session.getId());
    }
}
