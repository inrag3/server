import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;

import java.util.Arrays;

import io.netty.buffer.Unpooled;

public class Server {
    private int port;
    private int concurrentClientCount = 0;
    private boolean primeMode;
    private String adminLogin;
    private String adminPassword;


    public Server(int port, String adminLogin, String adminPassword) {
        this.port = port;
        this.concurrentClientCount = 0;
        this.primeMode = true;
        this.adminLogin = adminLogin;
        this.adminPassword = adminPassword;
    }

    public void run() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new ServerHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            // Bind and start to accept incoming connections.
            ChannelFuture f = b.bind(port).sync();

            // Wait until the server socket is closed.
            // In this example, this does not happen, but you can do that to gracefully
            // shut down your server.
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }


    public static void main(String[] args) throws Exception {
        String adminLogin = "admin";
        String adminPassword = "password";
        new Server(28563, adminLogin, adminPassword).run();
    }


    class ServerHandler extends ChannelInboundHandlerAdapter {
        String dataForProcessing = "";

        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            concurrentClientCount++;
            System.out.print(concurrentClientCount + " concurrent clients are connected\n");
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                String allQueriesInOneString = dataForProcessing + ((ByteBuf) msg).toString(CharsetUtil.UTF_8);
                String[] queries = allQueriesInOneString.split("\r\n");
                if (!allQueriesInOneString.substring(allQueriesInOneString.length() - 2).equals("\r\n")) {//если последний запрос неполный - запоминаем его для дальнейшего рассмотрения
                    dataForProcessing = queries[queries.length - 1];
                    queries = Arrays.copyOf(queries, queries.length - 1);
                } else dataForProcessing = "";
                for (String query : queries) {
                    String[] words = query.split(" ");
                    if (words[0].equals(adminLogin) && words[1].equals(adminPassword)) {
                        // аутентификация администратора

                        handleAdminCommand(ctx, words);
                    } else {
                        // обработка запросов клиентов
                        handleClientQuery(ctx, words);
                    }
                }
                ctx.flush();
            } finally {
                ((ByteBuf) msg).release();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            // Close the connection when an exception is raised.
            cause.printStackTrace();
            ctx.close();
            concurrentClientCount--;
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) {
            concurrentClientCount--;
        }

        private void handleAdminCommand(ChannelHandlerContext ctx, String[] words) {
            if (words.length > 2) {
                String command = words[2];
                if (command.equals("prime")) {
                    primeMode = true;
                    ctx.writeAndFlush(Unpooled.copiedBuffer("Admin command: Prime mode enabled\r\n", CharsetUtil.UTF_8));
                } else if (command.equals("power")) {
                    primeMode = false;
                    ctx.writeAndFlush(Unpooled.copiedBuffer("Admin command: Power mode enabled\r\n", CharsetUtil.UTF_8));
                } else {
                    ctx.writeAndFlush(Unpooled.copiedBuffer("Unknown admin command\r\n", CharsetUtil.UTF_8));
                }
            } else {
                ctx.writeAndFlush(Unpooled.copiedBuffer("Admin command format: [adminLogin] [adminPassword] [command]\r\n", CharsetUtil.UTF_8));
            }
        }

        private void handleClientQuery(ChannelHandlerContext ctx, String[] words) {
            if (primeMode) {
                checkPrime(ctx, words);
            } else {
                checkPowerOfTwo(ctx, words);
            }
        }

        private void checkPrime(ChannelHandlerContext ctx, String[] words) {
            try {
                int number = Integer.parseInt(words[0]);
                boolean isPrime = isPrime(number);
                String result = isPrime ? "Prime\r\n" : "Not prime\r\n";
                ctx.writeAndFlush(Unpooled.copiedBuffer(result, CharsetUtil.UTF_8));
            } catch (NumberFormatException e) {
                ctx.writeAndFlush(Unpooled.copiedBuffer("Invalid number\r\n", CharsetUtil.UTF_8));
            }
        }

        private void checkPowerOfTwo(ChannelHandlerContext ctx, String[] words) {
            try {
                int number = Integer.parseInt(words[0]);
                boolean isPowerOfTwo = isPowerOfTwo(number);
                String result = isPowerOfTwo ? "Power of two\r\n" : "Not power of two\r\n";
                ctx.writeAndFlush(Unpooled.copiedBuffer(result, CharsetUtil.UTF_8));
            } catch (NumberFormatException e) {
                ctx.writeAndFlush(Unpooled.copiedBuffer("Invalid number\r\n", CharsetUtil.UTF_8));
            }
        }

        private boolean isPrime(int number) {
            if (number <= 1) {
                return false;
            }
            for (int i = 2; i <= Math.sqrt(number); i++) {
                if (number % i == 0) {
                    return false;
                }
            }
            return true;
        }

        private boolean isPowerOfTwo(int number) {
            return (number & (number - 1)) == 0;
        }
    }
}

