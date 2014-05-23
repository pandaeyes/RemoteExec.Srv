package exec.srv;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;

import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.apache.mina.filter.executor.OrderedThreadPoolExecutor;
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
			int port = ExecService.getInstance().getPort();
			SocketAcceptor acceptor = new NioSocketAcceptor();
			SocketSessionConfig config = acceptor.getSessionConfig();
			config.setReadBufferSize(2048*1000);
			config.setMinReadBufferSize(1024);
			config.setMaxReadBufferSize(2048 * 1000);
			config.setIdleTime(IdleStatus.BOTH_IDLE, 120);
			config.setReuseAddress(true);
//			config.setTcpNoDelay(true);
			acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(new SmsCodecFactory(Charset.forName(("UTF-8"))))); 
			acceptor.getFilterChain().addLast("executor", new ExecutorFilter(new OrderedThreadPoolExecutor(16)));
			acceptor.setHandler(new ExecHandler());
			acceptor.bind(new InetSocketAddress(port));
			log.info("Server Started! " + port);
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}

}
