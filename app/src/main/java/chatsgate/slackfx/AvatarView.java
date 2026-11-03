package chatsgate.slackfx;

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

/// A person's picture as Slack draws it: a rounded square (corner 22% of the side, as Slack's own CSS has it). Until the
/// picture arrives, or when there is none, it shows the first letter of the name on a colour taken from the person's id.
public final class AvatarView extends StackPane {

    static final String[] COLORS = {"#2f6f4f", "#7a4b8c", "#a35c2b", "#2b5f8a", "#8a2b4f", "#4f6b2b", "#5b4a8a", "#2b7a7a"};

    final ImageView img = new ImageView();
    final Label initial = new Label();

    public AvatarView(double size) {
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        img.setFitWidth(size);
        img.setFitHeight(size);
        img.setSmooth(true);
        var clip = new Rectangle(size, size);
        var corner = Math.max(4, Math.min(size * 0.222, 12)) * 2;
        clip.setArcWidth(corner);
        clip.setArcHeight(corner);
        setClip(clip);
        initial.setStyle("-fx-font-family: 'Lato'; -fx-font-weight: bold; -fx-text-fill: white; -fx-font-size: " + Math.round(size * 0.5) + ";");
        getChildren().addAll(initial, img);
    }

    /// `image` may be null or still loading: the letter shows through until it is drawn.
    public AvatarView set(Image image, String name, String key) {
        img.setImage(image);
        initial.setText(name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase());
        setStyle("-fx-background-color: " + COLORS[Math.floorMod(key == null ? 0 : key.hashCode(), COLORS.length)] + ";");
        return this;
    }
}
