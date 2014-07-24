package main.http.login;

import java.io.File;
import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class TestHttpLogin {
	public static void main() throws IOException {
		File file = new File("/Users/zkbucciarati/Dev/HTTP_message.html");
		Document doc = Jsoup.parse(file, "UTF-8");
		Element form = doc.getElementById("form");
		Elements elements = form.select("input");
		elements.size();
	}
}
