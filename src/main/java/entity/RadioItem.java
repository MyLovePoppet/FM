package entity;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class RadioItem {
    private int id;
    private String title;
    private String desc;

    private static RadioItem EMPTY_ITEM = new RadioItem(0, "", "");

    public static RadioItem emptyItem() {
        return EMPTY_ITEM;
    }

    @Override
    public String toString() {
        return title + "\t" + desc;
    }
}
