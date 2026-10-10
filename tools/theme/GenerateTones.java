import com.google.android.material.color.utilities.TonalPalette;
/** Run with Material Components Android 1.12.0 classes.jar on the classpath. */
public class GenerateTones {
  public static void main(String[] args) {
    String[] seeds = {"2D2C2B", "3B197F", "8144A8", "F6F1EF", "63605D", "D73431", "3F9B65", "FFCE00", "0A8FD8", "E9A300", "EF6A9A", "00A4FF", "F47D31"};
    int[] tones = {0, 4, 6, 10, 12, 17, 20, 22, 24, 30, 40, 50, 60, 80, 90, 95, 98, 100};
    System.out.println("{");
    for (int i = 0; i < seeds.length; i++) {
      TonalPalette palette = TonalPalette.fromInt((int)Long.parseLong("FF" + seeds[i], 16));
      System.out.print("\"" + seeds[i] + "\":{");
      for (int j = 0; j < tones.length; j++) {
        System.out.printf("\"%d\":\"%08X\"%s", tones[j], palette.tone(tones[j]), j + 1 < tones.length ? "," : "");
      }
      System.out.println("}" + (i + 1 < seeds.length ? "," : ""));
    }
    System.out.println("}");
  }
}
