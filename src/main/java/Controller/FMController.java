package Controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import entity.RadioItem;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class FMController implements Initializable {
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private List<RadioItem> radioItems;
    private int currentSelectedIndex = 0;
    private boolean isPlaying = false;
    private MediaPlayer mediaPlayer;
    private Mac hMac;
    @FXML
    private ListView<RadioItem> radioListView;
    @FXML
    private Button play;
    @FXML
    private Label statusLabel;
    @FXML
    private Slider volumeSlide;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        httpClient = HttpClient.newHttpClient();
        objectMapper = new ObjectMapper();
        radioItems = new ArrayList<>(282);
        try {
            hMac = Mac.getInstance("HmacMD5");
            SecretKey secretKey = new SecretKeySpec("Lwrpu$K5oP".getBytes(), "HmacMD5");
            hMac.init(secretKey);
        } catch (NoSuchAlgorithmException | InvalidKeyException ignored) {
        }
        CompletableFuture.runAsync(() -> {
            int page = 1;
            while (true) {
                log.info("开始请求第[" + page + "]页数据");
                String currentBody = String.format("{\"query\":\"{\\n    radioPage(cid:442, page:%d){\\n      contents\\n    }\\n  }\"}", page);
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create("https://webbff.qingting.fm/www"))
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.182 Safari/537.36")
                        .POST(HttpRequest.BodyPublishers.ofString(currentBody))
                        .build();
                ArrayNode radioArrayJson;
                try {
                    HttpResponse<String> res = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                    JsonNode jsonNode = objectMapper.readTree(res.body());
                    radioArrayJson = jsonNode.path("data").path("radioPage").path("contents").withArray("items");
                } catch (IOException | InterruptedException | NullPointerException e) {
                    log.error("获取服务器回应数据错误[" + e.getMessage() + "]");
                    continue;
                }
                if (radioArrayJson.size() == 0) {
                    break;
                }
                log.info("第[" + page + "]页获取数据[" + radioArrayJson.size() + "]条数据");
                for (JsonNode node : radioArrayJson) {
                    int id = node.path("id").asInt(0);
                    String title = node.path("title").asText("");
                    String desc = node.path("desc").asText("");
                    RadioItem radioItem = new RadioItem(id, title, desc);
                    if (!radioItem.equals(RadioItem.emptyItem())) {
                        radioItems.add(radioItem);
                    }
                }
                page++;
            }
        }).whenComplete(((unused, throwable) -> {
            if (throwable != null) {
                log.warn("过程产生异常[" + throwable.getMessage() + "]");
            }
            log.info("更新电台列表数据，一共[" + radioItems.size() + "]条数据");
            Platform.runLater(this::updateRadioList);
        }));
        radioListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                currentSelectedIndex = radioListView.getSelectionModel()
                        .getSelectedIndex();
                updateMedia();
            }
        });
    }

    public void updateRadioList() {
        if (radioItems == null) {
            return;
        }
        radioListView.setItems(FXCollections.observableArrayList(radioItems));
    }

    public void updateMedia() {
        log.info("选择index[" + currentSelectedIndex + "]播放");
        RadioItem currentSelectedItem = radioItems.get(currentSelectedIndex);
        statusLabel.setText("正在播放：" + currentSelectedItem.getTitle() + "...");
        String url = getURLById(currentSelectedItem.getId());
        log.info("获取url地址为：" + url);
        Media media = new Media(url);
        if (media.getError() != null) {
            log.error("获取media失败[" + media.getError().getMessage() + "]");
            play.setStyle("-fx-background-image: url('pause.png');");
            isPlaying = false;
            return;
        }
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }
        mediaPlayer = new MediaPlayer(media);
        mediaPlayer.play();

        play.setStyle("-fx-background-image: url('play.png');");
        isPlaying = true;
    }

    /**
     * var t = "/live/".concat(e.channel_id, "/64k.mp3"),
     * n = encodeURIComponent(y().add(1, "hours").unix().toString(16)),
     * r = encodeURIComponent("web"),
     * i = encodeURIComponent(t),
     * a = "app_id=".concat(r, "&path=").concat(i, "&ts=").concat(n),
     * o = m.createHmac("md5", "Lwrpu$K5oP").update(a).digest("hex").toString();
     * return "".concat("//lhttp.qingting.fm").concat(t, "?app_id=").concat(r, "&ts=").concat(n, "&sign=").concat(encodeURIComponent(o))
     *
     * @param id 电台id
     * @return 真实的直播地址
     */
    public String getURLById(int id) {
        LocalDateTime localDateTime = LocalDateTime.now().plus(1, ChronoUnit.HOURS);
        String t = "/live/" + id + "/64k.mp3";
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

    public void playOrPause() {
        if (isPlaying) {
            mediaPlayer.pause();
            play.setStyle("-fx-background-image: url('pause.png');");
            isPlaying = false;
            statusLabel.setText("暂停播放：" + radioItems.get(currentSelectedIndex).getTitle() + "...");
        } else {
            mediaPlayer.play();
            play.setStyle("-fx-background-image: url('play.png');");
            isPlaying = true;
            statusLabel.setText("正在播放：" + radioItems.get(currentSelectedIndex).getTitle() + "...");

        }
    }

    public void prev() {
        currentSelectedIndex--;
        if (currentSelectedIndex < 0) {
            currentSelectedIndex = radioItems.size() - 1;
        }
        updateMedia();
    }

    public void next() {
        currentSelectedIndex++;
        if (currentSelectedIndex >= radioItems.size()) {
            currentSelectedIndex = 0;
        }
        updateMedia();
    }

    public void changeVolume() {
        if (mediaPlayer != null) {
            double volume = volumeSlide.getValue();
            log.info("设置声音[" + volume + "]...");
            mediaPlayer.setVolume(volume);
        }
    }
}
