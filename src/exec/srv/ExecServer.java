package exec.srv;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;

import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.SocketAcceptor;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import exec.common.SmsCodecFactory;

public class ExecServer {
	
	private final static Logger log = LoggerFactory.getLogger(ExecServer.class);

	public static void main(String [] args) {
		try {
			SocketAcceptor acceptor = new NioSocketAcceptor();
			SocketSessionConfig config = acceptor.getSessionConfig();
			config.setReadBufferSize(5120);
			config.setMinReadBufferSize(1024);
			config.setMaxReadBufferSize(5120 * 2);
			config.setIdleTime(IdleStatus.BOTH_IDLE, 120);
			config.setReuseAddress(true);
			
			acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(new SmsCodecFactory(Charset.forName(("UTF-8"))))); 
			acceptor.setHandler(new MyIoHandler());
			acceptor.bind(new InetSocketAddress(9123));
			log.info("Server Started!");
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}

}
