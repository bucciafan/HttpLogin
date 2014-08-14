package main.http.login;

import java.awt.FlowLayout;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.util.EntityUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
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

    private static String authKey = "5158DXUr2cH9aG9CHgjhFUrxJlWv8=";
    private static String selectNumberUrl = "http://esales.10010.com/pages/g3bsp/sub/postpaidsub/g3postpaid_sub_new.jsf?authKey=";
    private static String submitNumberUrl = "http://esales.10010.com/pages/g3bsp/sub/postpaidsub/g3postpaid_sub_new.jsf";
    private static String frameTopUrl = "http://esales.10010.com/pages/sys/frame/frameTop.jsf";
    
    private static String province = "38";
    private static String verifyCode;
    private Queue<String> numberQueue = new ConcurrentLinkedQueue<String>();
    
    private ExecutorService threadPool;
    
    private DefaultHttpClient httpClient;
    private String j_id2_SUBMIT = "";
    private String javax_faces_ViewState = "";

    private CountDownLatch latch;
    
    private Config config = new Config();

    public HttpLogin() throws Exception {
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));

        PoolingClientConnectionManager cm = new PoolingClientConnectionManager(schemeRegistry);
        cm.setMaxTotal(200);
        cm.setDefaultMaxPerRoute(20);
        
        httpClient = new DefaultHttpClient(cm);
        
        httpClient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 20000);
        httpClient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, 20000);

        readFiles();
        
        threadPool = Executors.newFixedThreadPool(config.threadNumber);
        latch = new CountDownLatch(config.threadNumber);
        
        if (StringUtils.isNotBlank(config.proxyIP) && StringUtils.isNotBlank(config.proxyPort)) {
            logger.info("设置代理，{}:{}", config.proxyIP, config.proxyPort);
            setProxy();
        }
    }

    private void setProxy() {
        if (httpClient != null) {
            HttpHost proxy = new HttpHost(config.proxyIP, Integer.parseInt(config.proxyPort));
            httpClient.getParams().setParameter("http.route.default-proxy", proxy);
        }
    }

    //访问主页
    public void accessHomePage() throws Exception {
        HttpGet httpGet = new HttpGet(hostUrl);
        HttpResponse response = this.httpClient.execute(httpGet);
        HttpEntity entity = response.getEntity();

        String homePage = EntityUtils.toString(entity, "UTF-8");

        Document doc = Jsoup.parse(homePage);

        this.j_id2_SUBMIT = doc.select("input[name=j_id2_SUBMIT]").val();
        this.javax_faces_ViewState = doc.select("input[name=javax.faces.ViewState]").val();

        EntityUtils.consume(entity);
        httpGet.abort();
    }

    //下载验证码
    private void downloadVerifyCodePic() throws Exception {
        String fileName = "yz.png";
        HttpGet httpget = new HttpGet(verifyCodeUrl + new Date().getTime());
        File file = new File(fileName);
        if (file.exists()) {
            file.delete();
        }

        HttpResponse response = this.httpClient.execute(httpget);
        HttpEntity entity = response.getEntity();
        InputStream in = entity.getContent();
        try {
            FileOutputStream out = new FileOutputStream(file);
            byte[] tmp = new byte[2048];
            while (in.read(tmp) != -1) {
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
        logger.info("验证码为：" + verifyCode);
        scanner.close();
        frame.dispose();
    }

    //显示验证码窗口
    private JFrame showVerifyPicWindow(String fileName) {
        JFrame frame = new JFrame();
        frame.setTitle("输入验证码");
        frame.setVisible(false);
        frame.setBounds(100, 100, 100, 100);
        frame.setLayout(new FlowLayout());
        ImageIcon icon = new ImageIcon(fileName);
        frame.add(new JLabel("请输入验证码！"));
        frame.add(new JLabel(icon));
        frame.setVisible(true);
        frame.setAlwaysOnTop(true);
        frame.setDefaultCloseOperation(3);
        return frame;
    }

    //发起登陆请求
    private void login() throws Exception {
        accessHomePage();
        downloadVerifyCodePic();

        HttpPost httpPost = new HttpPost(loginUrl);
        httpPost.setHeader("User-Agent","Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US) AppleWebKit/534.3 (KHTML, like Gecko) Chrome/6.0.472.63 Safari/534.3");
        List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();

        params.add(new BasicNameValuePair("AJAXREQUEST", "_viewRoot"));
        params.add(new BasicNameValuePair("_authKey", ""));
        params.add(new BasicNameValuePair("j_id2:province", province));
        params.add(new BasicNameValuePair("username", config.username));
        params.add(new BasicNameValuePair("password", config.password));
        params.add(new BasicNameValuePair("tokenPwd", ""));
        params.add(new BasicNameValuePair("verifyCode", verifyCode));
        params.add(new BasicNameValuePair("j_id2_SUBMIT", this.j_id2_SUBMIT));
        params.add(new BasicNameValuePair("javax.faces.ViewState", this.javax_faces_ViewState));
        params.add(new BasicNameValuePair("j_id2:j_id6", "j_id2:j_id6"));

        httpPost.setEntity(new UrlEncodedFormEntity(params));

        HttpResponse response = httpClient.execute(httpPost);
        if (!checkLogin(response)) {
            logger.info("登陆不成功！");
            HttpEntity entity = response.getEntity();
            logger.info(Jsoup.parse(EntityUtils.toString(entity, "UTF-8")).text());
            EntityUtils.consume(entity);
            System.exit(-1);
        }
        logger.info("登陆成功！");
        httpPost.releaseConnection();

        accessHomePage();
    }
    
    //检查登陆是否成功
    private boolean checkLogin(HttpResponse response) {
        boolean checkZoneCode = false;
        boolean checkJessionId = false;
        boolean checkUserToken = false;
        
        List<Cookie> cookies = httpClient.getCookieStore().getCookies();
        for (Cookie c : cookies) {
            if ("ZONECODE".equals(c.getName()) 
                    && StringUtils.isNotBlank(c.getValue())) {
                checkZoneCode = true;
            }
            if ("UserToken".equals(c.getName())
                    && StringUtils.isNotBlank(c.getValue())) {
                checkUserToken = true;
            }
            if ("JSESSIONID".equals(c.getName())
                    && StringUtils.isNotBlank(c.getValue())) {
                checkJessionId = true;
            }
        }
        return checkZoneCode && checkJessionId && checkUserToken;
    }

    //访问页面
    public String accessPage(String url) throws Exception {
        logger.info("访问页面，url: " + url);
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US) AppleWebKit/534.3 (KHTML, like Gecko) Chrome/6.0.472.63 Safari/534.3");
        httpGet.setHeader("Referer", "http://esales.10010.com/pages/sys/frame/frameSide.jsf");
        
        HttpResponse response = this.httpClient.execute(httpGet);
        HttpEntity entity = response.getEntity();
        String page = EntityUtils.toString(entity, "UTF-8");
        EntityUtils.consume(entity);
        httpGet.abort();
        
        return page;
    }
    
    private AtomicInteger numberCounter = new AtomicInteger(0);
    
    //选号页面表单
    private Map<String, String> formParams;
    
    //提交所有号码
    public void submitNumbers() throws Exception {
        if (StringUtils.isNotBlank(this.config.authKey)) {
            selectNumberUrl += this.config.authKey;
        }

        String selectNumberPage = accessPage(selectNumberUrl);
        formParams = fetchFormParams(selectNumberPage);
        
        addCheckboxParams(formParams);

        formParams.put("AJAXREQUEST", "_viewRoot");
        formParams.put("_authKey", authKey);
        formParams.put("similarityGroupingId", "form:j_id135");
        formParams.put("param2", "1");
        formParams.put("param3", "false");
        formParams.put("param4", "false");
        formParams.put("form:j_id135", "form:j_id135");
        formParams.put("jionRadio", "2");
        
        for (int i = 0; i < config.threadNumber; i++) {
            threadPool.execute(new submitWorker());
        }
        
        latch.await();
        threadPool.shutdown();
        
        logger.info("所有任务执行完成，退出");
        System.exit(-1);
    }
    
    //提交号码工作线程
    private final class submitWorker implements Runnable {
        public void run() {
            while(true) {
                String number = numberQueue.poll();
                
                if (number == null && numberQueue.isEmpty()) {
                    break;
                }
                
                String result = "";
                try {
                    result = submitNumber(number, formParams);
                    String message = String.format("号码%s：%s，处理结果：%s", numberCounter.incrementAndGet(), number, getResultMessage(result));
                    logger.info(message);
                } catch(Exception e) {
                    logger.info("提交号码异常！号码："+number, e);
                    numberQueue.offer(number);
                }
            }
            
            latch.countDown();
        }
    }

    //提交号码返回结果
    public String getResultMessage(String result) throws Exception {
        Elements elements = Jsoup.parse(result).select(".rich-messages");
        if (elements != null) {
            return elements.text();
        }
        return Jsoup.parse(result).text();
    }

    //提交单个号码
    private String submitNumber(String number, Map<String, String> formParams) throws Exception {
        String page = "";

        //请求参数
        List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
        for (Map.Entry<String, String> entry : formParams.entrySet()) {
            params.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        params.add(new BasicNameValuePair("g3NumselAfter_customNo", number));
        params.add(new BasicNameValuePair("param1", number));

        HttpPost httpPost = new HttpPost(submitNumberUrl);
        httpPost.setHeader("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US) AppleWebKit/534.3 (KHTML, like Gecko) Chrome/6.0.472.63 Safari/534.3");

        httpPost.setEntity(new UrlEncodedFormEntity(params));
        HttpResponse response;
        response = httpClient.execute(httpPost);
        HttpEntity entity = response.getEntity();
        page = EntityUtils.toString(entity, "UTF-8");

        httpPost.abort();
        EntityUtils.consume(entity);
            
        return page;
    }

    private void addCheckboxParams(Map<String, String> params) {
        params.put("groupFlag", "0");
        params.put("feeMode_firstMonthFeeModRadio", "01");
    }

    //提取选号页面表单参数
    public static Map<String, String> fetchFormParams(String page)
            throws IOException {
        Map<String, String> map = new HashMap<String, String>();
        Document doc = Jsoup.parse(page, "UTF-8");
        Element form = doc.getElementById("form");
        Elements elements = form.select("input");

        for (Element e : elements) {
            String type = e.attr("type");
            String name = e.attr("name");
            String value = e.attr("value");

            if ("button".equals(type)) {
                continue;
            } else if (("text".equals(type)) || ("hidden".equals(type))) {
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

    //读文件
    public void readFiles() throws Exception {
        readConfig();
        readExcel();
    }

    private void readExcel() throws Exception {
        File file = new File(this.config.excelFile);
        if (!file.exists()) {
            logger.info("excel文件不存在，程序退出！");
            System.exit(-1);
        }
        FileInputStream fis = new FileInputStream(file);
        POIFSFileSystem fs = new POIFSFileSystem(fis);
        HSSFWorkbook wb = new HSSFWorkbook(fs);
        HSSFSheet sheet = wb.getSheetAt(0);
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            HSSFRow row = sheet.getRow(rowIndex);
            if ((rowIndex == 0) || (row == null)) {
                continue;
            }
            String number = getNumber(row);
            if (StringUtils.isNotBlank(number)) {
                numberQueue.add(number);
            }
        }
        logger.info("读取Excel文件结束，共有{}个符合条件的号码", Integer.valueOf(numberQueue.size()));
    }

    private String getNumber(HSSFRow row) {
        String col1 = getStringCellValue(row.getCell(0));
        String col2 = getStringCellValue(row.getCell(1));
        String col3 = getStringCellValue(row.getCell(2));

        logger.debug(String.format("%s %s %s",
                new Object[] { col1, col2, col3 }));

        if ((StringUtils.isNotBlank(this.config.column2Condition))
                && (!this.config.column2Condition.equals(col2))) {
            return "";
        }

        if ((StringUtils.isNotBlank(this.config.column3Condition))
                && (!this.config.column3Condition.equals(col3))) {
            return "";
        }

        return col1;
    }

    private String getStringCellValue(HSSFCell cell) {
        if (cell != null) {
            String value;
            if (cell.getCellType() == 0)
                value = new DecimalFormat("0").format(cell
                        .getNumericCellValue());
            else {
                value = cell.getStringCellValue();
            }
            if (value != null)
                return value.trim();
        }
        return "";
    }

    private void readConfig() throws Exception {
        FileInputStream fis = null;
        File confFile = new File("config.properties");
        if (confFile.exists()) {
            fis = new FileInputStream(confFile);
        } else {
            logger.error("找不到propertise配置文件！程序终止！");
            System.exit(-1);
        }

        InputStream in = new BufferedInputStream(fis);
        Properties p = new Properties();
        p.load(in);
        fis.close();

        this.config.username = p.getProperty("username");
        checkConfigField("username", this.config.username);

        this.config.password = p.getProperty("password");

        this.config.excelFile = p.getProperty("excelFile");
        checkConfigField("excelFile", this.config.excelFile);

        this.config.column2Condition = new String(p.getProperty(
                "column2Condition").getBytes("ISO-8859-1"), "UTF-8");
        this.config.column3Condition = new String(p.getProperty(
                "column3Condition").getBytes("ISO-8859-1"), "UTF-8");

        this.config.authKey = p.getProperty("authKey");
        
        this.config.proxyIP = p.getProperty("proxyIP");
        this.config.proxyPort = p.getProperty("proxyPort");
        
        String threadNumberStr = p.getProperty("threadNumber");
        try {
            config.threadNumber =Integer.parseInt(threadNumberStr);
        } catch(Exception e) {
            config.threadNumber = 3;
        }
    }

    private void checkConfigField(String field, String value) {
        if (StringUtils.isBlank(value)) {
            logger.error("{}配置项值为空，退出！", field);
            System.exit(-1);
        }
    }

    public static void main(String[] args) throws Exception {
        HttpLogin login = new HttpLogin();
        login.login();
        login.accessPage(frameTopUrl);
        login.startAvoidTimeoutThread();
        login.submitNumbers();
    }
    
    private void startAvoidTimeoutThread() {
        new Thread() {
            public void run() {
                while(true) {
                    try {
                        Thread.sleep(50000);
                        logger.info("访问主页，避免超时");
                        HttpGet httpGet = new HttpGet(hostUrl);
                        HttpResponse response = httpClient.execute(httpGet);
                        HttpEntity entity = response.getEntity();
                        EntityUtils.consume(entity);
                        httpGet.abort();
                    } catch(Exception e) {
                        logger.info("avoidTimeoutThread error!", e);
                    }
                }
            }
        }.start();
    }
    
    private final class Config {
        private String username;
        private String password;
        private String excelFile;
        private String column2Condition;
        private String column3Condition;
        private String authKey;
        private String proxyIP;
        private String proxyPort;
        private int threadNumber;
    }
}
