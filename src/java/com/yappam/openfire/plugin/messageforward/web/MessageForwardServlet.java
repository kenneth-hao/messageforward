package com.yappam.openfire.plugin.messageforward.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.jivesoftware.admin.AuthCheckFilter;
import org.jivesoftware.openfire.MessageRouter;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Message.Type;

import com.yappam.openfire.plugin.MessageForwardPlugin;

public class MessageForwardServlet extends HttpServlet {
	
	private static final long serialVersionUID = 1L;
	
	private static final Logger logger = LoggerFactory.getLogger(MessageForwardServlet.class);
	
	private ObjectMapper mapper = new ObjectMapper();
	
	private MessageRouter messageRoute;
	
	private final String KEY_RESULT = "result";
	private final String KEY_MSG = "msg";
	
	@Override  
    public void init() throws ServletException {  
		logger.info("MessageForwardServlet 初始化"); 
		messageRoute = XMPPServer.getInstance().getMessageRouter();
		
		AuthCheckFilter.addExclude("messageforward");
    }  
  
    @Override  
    public void destroy() {  
    	logger.info("MessageForwardServlet 销毁");  
    }  
  
    @Override  
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)  
            throws ServletException, IOException {
    	Map<String, Object> map = new HashMap<String, Object>();
    	
    	// 转发消息的来源Resource
    	String forwardResource = JiveGlobals.getProperty(MessageForwardPlugin.FORWARD_KEY_RESOURCES_REST);
    	if (forwardResource == null || StringUtils.isEmpty(forwardResource)) {
    		map.put(KEY_MSG, 
    				String.format("通过RESTApi 转发消息时的Resource 来源未设置, 消息转发失败. 请设置系统属性<%s>", 
    						MessageForwardPlugin.FORWARD_KEY_RESOURCES_REST));
	    	map.put(KEY_RESULT, false);
			out(resp, map);
			return ;
		}
    	
    	// 接收人的Resourcce
    	final String targetResource = "APP";
    	
    	String sender  = req.getParameter("sender");
    	
    	List<String> errMsgs = new ArrayList<String>();
    	if (StringUtils.isEmpty(sender)) {
    		errMsgs.add("参数 [sender] 不能为空");
    	}
    	String receiver = req.getParameter("receiver");
    	if (StringUtils.isEmpty(receiver)) {
    		errMsgs.add("参数 [receiver] 不能为空");
    	
    	}
    	String body = req.getParameter("message");
    	if (StringUtils.isEmpty(body)) {
    		errMsgs.add("参数 [message] 不能为空");
    		
    	}
    	
    	if (errMsgs.size() > 0) {
    		map.put(KEY_MSG, Arrays.toString(errMsgs.toArray()));
	    	map.put(KEY_RESULT, false);
			out(resp, map);
			return ;
    	}
    	
    	JID senderJid = new JID(sender, XMPPServer.getInstance().getServerInfo().getXMPPDomain(), forwardResource);
    	JID receiverJid = new JID(receiver, XMPPServer.getInstance().getServerInfo().getXMPPDomain(), targetResource);
    	// 用户是否有效
    	if (UserManager.getInstance().isRegisteredUser(senderJid) == false) {
    		errMsgs.add(String.format("消息发送人[%s]未注册", sender));
    	}
    	if (UserManager.getInstance().isRegisteredUser(receiverJid) == false) {
    		errMsgs.add(String.format("消息接收人[%s]不存在", receiver));
    	}
    	
    	if (errMsgs.size() > 0) {
    		map.put(KEY_MSG, Arrays.toString(errMsgs.toArray()));
	    	map.put(KEY_RESULT, false);
			out(resp, map);
			return ;
    	}
    	
    	// 没啥用
        String subject = "subject";  
        
        try {
        	Message message = pushMessage(receiverJid, senderJid, body, subject);
    		map.put(KEY_RESULT, true);
    		map.put(KEY_MSG, String.format("消息转发成功. \nMessage:%s", message.toString()));
    		out(resp, map);
        } catch(PacketException pe) {
        	logger.info(pe.getMessage());
        	map.put(KEY_MSG, "服务器出现错误!");
    		map.put(KEY_RESULT, false);
    		out(resp, map);
        }

    }  
  
    /**
     * 发送消息
     * 
     * @param to 目标用户
     * @param from 来源用户
     * @param body 消息内容
     * @param subject 主题
     * @throws PacketException
     */
    private Message pushMessage(JID to, JID from, String body, String subject) throws PacketException {  
        Message message = new Message();  
        message.setFrom(from);
        message.setTo(to);
        message.setBody(body);
        message.setSubject(subject);
        message.setType(Type.chat);
        
        // 直接发送消息, 跳过消息拦截器处理
        // XMPPServer.getInstance().getRoutingTable().routePacket(to, message, true);
        
        // 路由到拦截器中, 最后发送消息
        messageRoute.route(message);
        return message;
    }  
    
    private void out(HttpServletResponse response, Map<String, Object> map) throws ServletException, IOException {
    	if ((Boolean)map.get(KEY_RESULT) == false) {
    		logger.error(map.get(KEY_MSG).toString());
    	} else {
    		logger.info(map.get(KEY_MSG).toString());
    	}
    	response.setContentType("application/json; charset=UTF-8");
		PrintWriter out = response.getWriter();
		StringWriter writer = new StringWriter();
		mapper.writeValue(writer, map);
		out.println(writer.toString());
		out.flush();
	}
	
}
