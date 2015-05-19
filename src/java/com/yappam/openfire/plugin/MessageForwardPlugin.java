package com.yappam.openfire.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

public class MessageForwardPlugin implements Plugin, PacketInterceptor {
	
	private static final Logger logger = LoggerFactory.getLogger(MessageForwardPlugin.class);
	
	// 推送到微信的转发地址
	public static final String FORWARD_KEY_WECHAT_URL = "plugin.messageforward.forwardWechatUrl";
	
	// 支持消息转发的资源
	public static final String FORWARD_KEY_RESOURCES_MOBILE = "plugin.messageforward.sourceResources.mobile";
	
	// 支持消息转发的资源
	public static final String FORWARD_KEY_RESOURCES_REST = "plugin.messageforward.sourceResources.rest";
	
	private InterceptorManager interceptorManager = InterceptorManager.getInstance();

	@Override
	public void initializePlugin(PluginManager manager, File pluginDirectory) {
		if (JiveGlobals.getProperty(FORWARD_KEY_WECHAT_URL) == null) {
			JiveGlobals.setPropertyEncrypted(FORWARD_KEY_WECHAT_URL, false);
			JiveGlobals.setProperty(FORWARD_KEY_WECHAT_URL, "");
		}
		if (JiveGlobals.getProperty(FORWARD_KEY_RESOURCES_MOBILE) == null) {
			JiveGlobals.setPropertyEncrypted(FORWARD_KEY_RESOURCES_MOBILE, false);
			JiveGlobals.setProperty(FORWARD_KEY_RESOURCES_MOBILE, "");
		}
		if (JiveGlobals.getProperty(FORWARD_KEY_RESOURCES_REST) == null) {
			JiveGlobals.setPropertyEncrypted(FORWARD_KEY_RESOURCES_REST, false);
			JiveGlobals.setProperty(FORWARD_KEY_RESOURCES_REST, "");
		}
		
		interceptorManager.addInterceptor(this);
	}

	@Override
	public void destroyPlugin() {
		interceptorManager.removeInterceptor(this);
	}
	
	@Override
	public void interceptPacket(Packet packet, Session session,
			boolean incoming, boolean processed) throws PacketRejectedException {
        JID recipient = packet.getTo();
        if (recipient != null) {
            String username = recipient.getNode();
            // 广播消息或是不存在/没注册的用户.
            if (username == null || !UserManager.getInstance().isRegisteredUser(recipient)) {
                return;
            } 
            if (!XMPPServer.getInstance().getServerInfo().getXMPPDomain().equals(recipient.getDomain())) {
                // 非当前openfire服务器信息
                return;	
            } 
            if ("".equals(recipient.getResource())) {
            	
            }
        }
        this.doAction(packet, incoming, processed, session);
	}
	
	private void doAction(Packet packet, boolean incoming, boolean processed, Session session) {
		Packet copyPacket = packet.createCopy();

		if (packet instanceof Message) {
			Message message = (Message) copyPacket;

			if (message.getType() == Message.Type.chat || message.getType() == Message.Type.groupchat) {
				if (session == null) {
					return;
				}
				if (processed || !incoming) {
					return;
				}
				
                JID recipient = message.getTo();
                
				try {
	                if (recipient.getNode() == null  
	                		|| !UserManager.getInstance().isRegisteredUser(  
                                recipient.getNode())) {  
                    // Sender is requesting presence information of an  
                    // anonymous user  
	                	throw new UserNotFoundException("Username is null");  
	                }
	                
	                
	                String sourceResource = JiveGlobals.getProperty(FORWARD_KEY_RESOURCES_MOBILE);
	                if (sourceResource == null || StringUtils.isEmpty(sourceResource)) {
	        			logger.error("支持消息转发的 Resources 来源未设置, 消息转发失败. 请设置系统属性<{}>", FORWARD_KEY_RESOURCES_MOBILE);
	        			return ;
	        		}
	                sourceResource = sourceResource.trim();
	                
	                // 发送者 JID
	                JID senderJid = session.getAddress();
	                String targetResource = senderJid.getResource();
	                if (targetResource.equals(sourceResource) == false) {
	                	logger.error("支持消息转发的 Resource 不支持[{}]. Resource来源仅支持 {}", targetResource, sourceResource);
	        			return ;
	                }
	                
                	String sender = senderJid.getNode();
                	String content = message.getBody();
                	if (content == null) {
                		logger.warn("消息内容为空, 不进行转发.");
                		return ;
                	}
                	// 消息接收人
                	String receiver = recipient.getNode();
                	
                	String forwardUrl = JiveGlobals.getProperty(FORWARD_KEY_WECHAT_URL);
                	if (StringUtils.isEmpty(forwardUrl)) {
        				logger.error("请初始化消息转发 de 目标的服务器地址! 请设置系统属性<{}>", FORWARD_KEY_WECHAT_URL);
        				return ;
        			}
                	 
                	try {
            		    HttpParams httpParameters = new BasicHttpParams();
            		    HttpConnectionParams.setConnectionTimeout(httpParameters, 10*1000); //设置请求超时10秒
            		    HttpConnectionParams.setSoTimeout(httpParameters, 10*1000); //设置等待数据超时10秒
            		    HttpConnectionParams.setSocketBufferSize(httpParameters, 8192);
            		    HttpClient httpClient = new DefaultHttpClient(httpParameters); //此时构造DefaultHttpClient时将参数传入 
            		    
            			HttpPost post = new HttpPost(forwardUrl);
            			logger.info("消息转发到 {}", forwardUrl);
            			
            			post.getParams().setParameter("http.protocol.content-charset",HTTP.UTF_8);  
            			post.getParams().setParameter(HTTP.CONTENT_ENCODING, HTTP.UTF_8);  
            			post.getParams().setParameter(HTTP.CHARSET_PARAM, HTTP.UTF_8);  

            			// 参数内容
            			String sendMsg = String.format("{\"from\":\"%s\", \"to\":\"%s\", \"message\":\"%s\"}", sender, receiver, message.getBody());
            			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
            			nvps.add(new BasicNameValuePair("msg", sendMsg));
            			
            			logger.info("转发 de 消息内容: {}", sendMsg);
            	
            			post.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));
            			
            			HttpResponse response = httpClient.execute(post);
            			
            			int status = response.getStatusLine().getStatusCode();
            			if (status == 200) {
            				logger.info("消息转发成功.");
            			} else {
            				logger.error("消息转发失败, HttpStatusCode: {}", status);
            			}
            			
            			httpClient.getConnectionManager().shutdown(); 
            			
            		} catch (IllegalStateException e) {
            			String errMsg = String.format("请确认转发消息的服务器地址 [%s] 是否可用 \n %s ", forwardUrl, e.getMessage());
            			logger.error(errMsg);
            		} catch (Exception e) {
            			logger.error(e.getMessage());
            		}
				} catch (UserNotFoundException  e) {
					logger.warn("exceptoin " + recipient.getNode() + " not find"  
                            + ",full jid: " + recipient.toFullJID()); 
				}
			}
		}
	}

	
}
