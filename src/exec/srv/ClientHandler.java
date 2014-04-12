package exec.srv;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import exec.proto.SmsObject;

public class ClientHandler extends IoHandlerAdapter {

	private final static Logger log = LoggerFactory.getLogger(ClientHandler.class);

	private final String values;

	public ClientHandler(String values) {
		this.values = values;
	}
	
	public void messageReceived(IoSession session, Object message) throws Exception {
		SmsObject sms = (SmsObject)message;
		String str = sms.getContent();
		log.info("==========clien==messageReceived:" + str + " ee " + sms.getParam());
	}

	@Override
	public void sessionOpened(IoSession session) {
		log.info(values);
		SmsObject sms = new SmsObject();
		sms.setProto(101);
		sms.setContent(values);
		sms.setParam("我是参数");
		session.write(sms);
	}
}
