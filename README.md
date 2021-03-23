[![67SbA1.png](https://z3.ax1x.com/2021/03/23/67SbA1.png)](https://imgtu.com/i/67SbA1)

release下载地址：https://github.com/MyLovePoppet/FM/releases/download/1.1/FM.jar

使用方式：
```shell script
java -jar FM.jar
```

# 蜻蜓FM的真实电台直播推流地址获取过程
***前言：蜻蜓FM的音乐电台功能挺不错的，就是在注册开发者时，只允许企业用户注册，学生和个人用户就没办法了，所以只能从网页端入手了。***


首先打开一个音乐电台的网页：https://www.qingting.fm/radios/4847/ ，打开F12，可以看到这个电台的网页的听取地址为：https://lhttp.qingting.fm/live/4847/64k.mp3?app_id=web&ts=6042301c&sign=543e21c45f8af5b8799cf58bc7d93f89

可以看到这个链接带了三个参数，分别是app_id，ts和sign，这三个参数缺一不可，而且ts和sign这两个参数乍一看不知道是什么，那就直接全文搜索就好了，全文搜索sign关键字，可以看到主要的生成这两个字段的js代码如下：
```javascript
{
	key: "_getLiveUrl",
	value: function(e) {
		if (this._useNewStreaming(e)) {
			var t = "/live/".concat(e.channel_id, "/64k.mp3"),
			n = encodeURIComponent(y().add(1, "hours").unix().toString(16)),
			r = encodeURIComponent("web"),
			i = encodeURIComponent(t),
			a = "app_id=".concat(r, "&path=").concat(i, "&ts=").concat(n),
			o = m.createHmac("md5", "Lwrpu$K5oP").update(a).digest("hex").toString();
			return "".concat("//lhttp.qingting.fm").concat(t, "?app_id=").concat(r, "&ts=").concat(n, "&sign=").concat(encodeURIComponent(o))
		}
		return "//http.qingting.fm/".concat(e.resource_id, ".mp3")
	}
}
```
这里我们按照这段js代码来计算一下：
```javascript
var t="/live/4847/64k.mp3",

//这里猜测函数y()就是获取当前的时间，然后加上一个小时，最后转化成unix时间戳后转成16进制
//在链接的地址中我们可以知道n=6042301c，反推一下证明我们的猜测是正确的
n=timestamp().add(1,"hours").unix().toString(16)
//需要urlencode一下，不过结果都是一样的
    --> n="6042301c"

r=urlencode("web")
    --> r="web"

i=urlencode(t)
    //这里需要注意的是js与java语言urlencode出来的结果是不一样的，js urlencode出来的是大写的控制符，java出来的是小写的
    --> i="%2Flive%2F4847%2F64k.mp3"


a="app_id="+r+"&path="+i+"&ts="+n
    --> "app_id=web&path=%2Flive%2F4847%2F64k.mp3&ts=6042301c"

o = m.createHmac("md5", "Lwrpu$K5oP").update(a).digest("hex").toString();
    //这里其实就是使用hmacmd5对a进行散列加密了一下，密钥是"Lwrpu$K5oP"
    --> o=HmacMd5("Lwrpu$K5oP").update(a)
    --> o="543e21c45f8af5b8799cf58bc7d93f89"

url="".concat("//lhttp.qingting.fm").concat(t, "?app_id=").concat(r, "&ts=").concat(n, "&sign=").concat(encodeURIComponent(o))
    //连接一下字符串即可
    --> url="https://lhttp.qingting.fm/live/4847/64k.mp3?app_id=web&ts=6042301c&sign=543e21c45f8af5b8799cf58bc7d93f89"
```
这里我们使用Java代码复现一下这段js代码
```java
public String getURLById(int id) {
    Mac hMac = Mac.getInstance("HmacMD5");
    SecretKey secretKey = new SecretKeySpec("Lwrpu$K5oP".getBytes(), "HmacMD5");
    hMac.init(secretKey);

    String t = "/live/" + id + "/64k.mp3";
    //当前时间加一小时
    LocalDateTime localDateTime = LocalDateTime.now().plus(1, ChronoUnit.HOURS);
    //小写
    String n = Long.toHexString(localDateTime.atZone(ZoneId.systemDefault()).toEpochSecond()).toLowerCase();
    String r = "web";
    //手动URLEncode
    String i = t.replaceAll("/", "%2F");
    String a = "app_id=" + r + "&path=" + i + "&ts=" + n;
    //hMacMd5
    hMac.update(a.getBytes());
    String o = byte2HexString(hMac.doFinal());
    return "https://lhttp.qingting.fm" + t + "?app_id=" + r + "&ts=" + n + "&sign=" + o;
}
public static final char[] DIGITS_LOWER = {'0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

public static String byte2HexString(final byte[] data) {
    final int l = data.length;
    final char[] out = new char[l << 1];
    // two characters form the hex value.
    for (int i = 0, j = 0; i < l; i++) {
        out[j++] = DIGITS_LOWER[(0xF0 & data[i]) >>> 4];
        out[j++] = DIGITS_LOWER[0x0F & data[i]];
    }
    return new String(out);
}
```
需要注意的只有java urlencode出来的结果是小写的，与js的结果不同，后续需要这个结果进行散列，这里卡了挺久的。

获取到了真实地址，使用JavaFX的Media和MediaPlayer配合播放即可。