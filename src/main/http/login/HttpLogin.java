package main.http.login;

import java.awt.FlowLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpLogin {
	private static Logger logger = LoggerFactory.getLogger(HttpLogin.class);
	
	private static String hostUrl = "http://esales.10010.com";
	private static String loginUrl = "http://esales.10010.com/pages/sys/sys_login.jsf";
	private static String verifyCodeUrl = "http://esales.10010.com/pages/sys/frame/frameValidationServlet?randamCode=";
	private static String topFrameUrl = "http://esales.10010.com/pages/sys/frame/frameTop.jsf";
	
	private static String frameContentUrl = "http://esales.10010.com/pages/sys/frame/frameContent.jsf";
	private static String authKey = "5158DXUr2cH9aG9CHgjhFUrxJlWv8=";
	private static String selectNumberUrl = "http://esales.10010.com/pages/g3bsp/sub/postpaidsub/g3postpaid_sub_new.jsf?authKey=5158DXUr2cH9aG9CHgjhFUrxJlWv8%3D";
	private static String submitNumberUrl = "http://esales.10010.com/pages/g3bsp/sub/postpaidsub/g3postpaid_sub_new.jsf";
	
	private static String username = "";
	private static String password = "";
	private static String province = "";
	private static String verifyCode;
	
	private static List<String> numberList = Arrays.asList("18650627324", 
															"18605070411", 
															"18605070444", 
															"18605070543", 
															"18605070654");

	//18650627324
	private DefaultHttpClient httpClient;
	
	private HttpLogin() {
		httpClient = new DefaultHttpClient();
		setProxy();
	}
	
	private void setProxy() {
		httpClient = new DefaultHttpClient();
		HttpHost proxy = new HttpHost("127.0.0.1", 8080);
		httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
	}
	
	private String j_id2_SUBMIT = "";
	private String javax_faces_ViewState = "";
	
	public void accessHomePage() throws Exception {
        HttpGet httpGet = new HttpGet(hostUrl);
        HttpResponse response = httpClient.execute(httpGet);
        HttpEntity entity = response.getEntity();
        
        String homePage = EntityUtils.toString(entity, "UTF-8");
        
        Document doc = Jsoup.parse(homePage);
        //info(doc.toString());
        httpGet.releaseConnection();
        
        j_id2_SUBMIT = doc.select("input[name=j_id2_SUBMIT]").val();
        javax_faces_ViewState = doc.select("input[name=javax.faces.ViewState]").val();
        
        EntityUtils.consume(entity);
        
//        info("javax.faces.ViewState: "+javax_faces_ViewState);
//        info("j_id2_SUBMIT: "+j_id2_SUBMIT);
    }
	
	private void downloadVerifyCodePic() throws Exception {
        String fileName = "yz.png";
        HttpGet httpget = new HttpGet(verifyCodeUrl+new Date().getTime());
        File file = new File(fileName);
        if (file.exists()) {
            file.delete();
        }
        
        HttpResponse response = httpClient.execute(httpget);
        HttpEntity entity = response.getEntity();
        InputStream in = entity.getContent();
        try {
        	FileOutputStream out = new FileOutputStream(file);
            byte[] tmp = new byte[2048]; 
            while ((in.read(tmp)) != -1) {
                out.write(tmp);
            }
            out.close();
        } finally {
            in.close();
        }
        EntityUtils.consume(entity);
        logger.info("验证码下载完成，请查看验证码并输入！");
        httpget.releaseConnection();
        
        JFrame frame = showVerifyPicWindow(fileName);
        
        Scanner scanner = new Scanner(System.in);
		verifyCode = scanner.nextLine();
		logger.info("验证码为："+verifyCode);
		scanner.close();
		frame.dispose();
	}
	
	private JFrame showVerifyPicWindow(String fileName) {
		JFrame frame = new JFrame();
		frame.setTitle("输入验证码");
		frame.setVisible(false);
		frame.setBounds(100, 100, 100, 100);
		frame.setLayout(new FlowLayout());
		ImageIcon icon = new ImageIcon(fileName);
		frame.add(new JLabel("请输入验证码！"));
		frame.add(new JLabel(icon));
		JTextField txt=new JTextField();
		frame.add(txt);
		frame.setVisible(true);
		frame.setAlwaysOnTop(true);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		return frame;
	}
	
	//先访问主页拿到session，再下载验证码，再发送登录请求
	//登录后会拿到4个cookie值
	private void login() throws Exception {
		accessHomePage();
		downloadVerifyCodePic();
		
		HttpPost httpPost = new HttpPost(loginUrl);
		httpPost.setHeader("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US) AppleWebKit/534.3 (KHTML, like Gecko) Chrome/6.0.472.63 Safari/534.3");
        List<NameValuePair> params = new ArrayList<NameValuePair>();

        params.add(new BasicNameValuePair("AJAXREQUEST", "_viewRoot"));
        params.add(new BasicNameValuePair("_authKey", ""));
        params.add(new BasicNameValuePair("j_id2:province", province));
        params.add(new BasicNameValuePair("username", username));
        params.add(new BasicNameValuePair("password", password));
        params.add(new BasicNameValuePair("tokenPwd", ""));
        params.add(new BasicNameValuePair("verifyCode", verifyCode));
        params.add(new BasicNameValuePair("j_id2_SUBMIT", j_id2_SUBMIT));
        params.add(new BasicNameValuePair("javax.faces.ViewState", javax_faces_ViewState));
        params.add(new BasicNameValuePair("j_id2:j_id6", "j_id2:j_id6"));
        
        httpPost.setEntity(new UrlEncodedFormEntity(params));
        
        httpClient.execute(httpPost);
        
        httpPost.releaseConnection();
		
        //一定要再次访问首页
        accessHomePage();
	}
	
	public String accessPage(String url) throws Exception {
		logger.info("访问页面，url: " + url);
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US) AppleWebKit/534.3 (KHTML, like Gecko) Chrome/6.0.472.63 Safari/534.3");
        HttpResponse response = httpClient.execute(httpGet);
        HttpEntity entity = response.getEntity();
        String page = EntityUtils.toString(entity, "UTF-8");
        //info(indexPage);
        httpGet.releaseConnection();
        EntityUtils.consume(entity);
        
        return page;
    }
	
	public void testSubmitNumber() throws Exception {
		String page = accessPage(selectNumberUrl);
		//info("selectNumerPage: "+page);
		Map<String, String> formParams = fetchFormParams(page);
		
		for (String number : numberList) {
			String result = submitNumber(number, formParams);
			String message = String.format("号码：%s，处理结果：%s", number, getResultMessage(result));
			logger.info(message);
		}
		
	}
	
	public String getResultMessage(String result) throws Exception {
		Elements elements = Jsoup.parse(result).select(".rich-messages");
		if (elements != null)
			return elements.text();
		else 
			return Jsoup.parse(result).text();
	}
	
	private String submitNumber(String number, Map<String, String> formParams) throws Exception {
		addCheckboxParams(formParams);
		
		formParams.put("g3NumselAfter_customNo", number);
		formParams.put("AJAXREQUEST", "_viewRoot");
		formParams.put("_authKey", authKey);
		
		formParams.put("similarityGroupingId", "form:j_id135");
		formParams.put("param1", number);
		formParams.put("param2", "1");
		formParams.put("param3", "false");
		formParams.put("param4", "false");
		formParams.put("form:j_id135", "form:j_id135");
		formParams.put("jionRadio", "2");
		
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		for (Map.Entry<String, String> entry : formParams.entrySet()) {
			params.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
		}
		
		HttpPost httpPost = new HttpPost(submitNumberUrl);
		httpPost.setHeader("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US) AppleWebKit/534.3 (KHTML, like Gecko) Chrome/6.0.472.63 Safari/534.3");
		
		httpPost.setEntity(new UrlEncodedFormEntity(params));
		
		HttpResponse response = httpClient.execute(httpPost);
		HttpEntity entity = response.getEntity();
        String page = EntityUtils.toString(entity, "GBK");
        
        httpPost.releaseConnection();
        
        return page;
	}
	
	/*
		checkbox的值无法通过解析页面拿到，要特殊处理，目前有两个
		1.用户类型，两个个选项，集团：1，公众：0，目前值为0
		2.入网当月资费为，3个选项，套餐包外资费：01，全月套餐：02，套餐减半：03，目前值为01
	 */
	private void addCheckboxParams(Map<String, String> params) {
		params.put("groupFlag", "0");
		params.put("feeMode_firstMonthFeeModRadio", "01");
	}
	
	public static Map<String, String> testForm(String page) throws IOException {
		Map<String, String> map = new HashMap<>();
		File file = new File("/Users/zkbucciarati/Dev/HTTP_message/index_request.html");
		Document doc = Jsoup.parse(file, "UTF-8");
		Element form = doc.getElementById("form");
		Elements elements = form.select("input");
		//System.out.println(elements);
		
		for (Element e : elements) {
			String type = e.attr("type");
			String name = e.attr("name");
			String value = e.attr("value");
			
			if ("button".equals(type)) {
				continue;
			} else if ("text".equals(type) || "hidden".equals(type)) {
				map.put(name, value);
			} else if ("checkbox".equals(type)) {
				String checked = e.attr("checked");
				if ("checked".equals(checked)) {
					map.put(name, "on");
				} else {
					map.put(name, "");
				}
			} else if ("radio".equals(type)) {
				map.put(name, value);
			}
		}
		
		for(Map.Entry<String, String> entry : map.entrySet()) {
			if (entry.getKey().equals("javax.faces.ViewState"))
				continue;
			//System.out.println(entry.getKey()+" : "+entry.getValue());
		}
		return map;
	}
	
	public static Map<String, String> fetchFormParams(String page) throws IOException {
		Map<String, String> map = new HashMap<>();
		Document doc = Jsoup.parse(page, "UTF-8");
		Element form = doc.getElementById("form");
		Elements elements = form.select("input");
		
		for (Element e : elements) {
			String type = e.attr("type");
			String name = e.attr("name");
			String value = e.attr("value");
			
			if ("button".equals(type)) {
				continue;
			} else if ("text".equals(type) || "hidden".equals(type)) {
				map.put(name, value);
			} else if ("checkbox".equals(type)) {
				String checked = e.attr("checked");
				if ("checked".equals(checked)) {
					map.put(name, "on");
				} else {
					map.put(name, "");
				}
			} else if ("radio".equals(type)) {
				map.put(name, value);
			}
		}
		
		return map;
	}
	
	public void process() throws Exception {
		HttpLogin login = new HttpLogin();
		login.accessHomePage();
		login.downloadVerifyCodePic();
		login.login();
		login.accessHomePage();
		login.accessPage(topFrameUrl);
		String contentPage = login.accessPage(frameContentUrl);
//		String authKey = login.fetchAuthKey(sideFramePage);
//		info("authKey: "+authKey);
		//login.accessPage(submitUrl+"?"+authKey);
		String selectNumberPage = login.accessPage(selectNumberUrl);
		login.httpClient.getConnectionManager().shutdown();
	}
	
	
	public static void main(String[] args) throws Exception {
		HttpLogin login = new HttpLogin();
		login.login();
		//login.accessPage(topFrameUrl);
		
		login.testSubmitNumber();
		//login.accessPage(selectNumberUrl);
		//login.accessPage(submitNumberUrl);
		//		testForm("");
		
	}
}
