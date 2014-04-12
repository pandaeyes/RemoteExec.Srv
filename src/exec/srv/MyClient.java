package exec.srv;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;

import org.apache.mina.core.service.IoConnector;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketConnector;

import exec.common.SmsCodecFactory;

public class MyClient {

	public static void main(String[] args) {
		IoConnector connector = new NioSocketConnector();
		connector.setConnectTimeoutMillis(30000);
		connector.getFilterChain().addLast("codec", new ProtocolCodecFilter(new SmsCodecFactory(Charset.forName(("UTF-8")))));
		connector.setHandler(new ClientHandler("你好大家好")); 
		connector.connect(new InetSocketAddress("localhost", 9123));
	}
}
