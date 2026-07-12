package nl.groenstad.groenopdebalans;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private PdfRenderer renderer;
    private ParcelFileDescriptor descriptor;
    private PdfPageView pdfView;
    private TextView pageTitle;
    private EditText pageInput;
    private ProgressBar progress;
    private ImageButton bookmarkButton;
    private int pageIndex = 0;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Set<String> bookmarks;

    private static final String[] CHAPTERS = {
        "Voorwoord|9", "1. Het Versteende Tijdperk|11", "2. De Groene Stad als innovatieve hot spot|21",
        "3. Van norm naar waarde|29", "4. Aangenaam verpozen|37", "5. Groen vast goed|45",
        "6. Eten wat de stad schaft|55", "7. De rijkdom van de rustplaats|63",
        "8. Mensenrijk, dierenrijk en plantenrijk|71", "9. Aan de straatstenen niet kwijt|79",
        "10. Werk aan de winkel|89", "11. Wat heet warm|97",
        "12. Over groene longen en leven van de lucht|105",
        "13. Van kantoortuin tot groen kantoor tot groene werkplaats|115",
        "14. Kom je buiten spelen?|123", "15. Van zorg voor meer groen tot minder zorg door meer groen|133",
        "16. Over nieuw eigenaarschap en nieuwe collectiviteit|143", "17. Epiloog: De balans opgemaakt|149"
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pdfView = findViewById(R.id.pdfView);
        pageTitle = findViewById(R.id.pageTitle);
        pageInput = findViewById(R.id.pageInput);
        progress = findViewById(R.id.progress);
        bookmarkButton = findViewById(R.id.bookmarkButton);
        bookmarks = new HashSet<>(getPreferences(MODE_PRIVATE).getStringSet("bookmarks", new HashSet<>()));

        findViewById(R.id.previousButton).setOnClickListener(v -> showPage(pageIndex - 1));
        findViewById(R.id.nextButton).setOnClickListener(v -> showPage(pageIndex + 1));
        findViewById(R.id.menuButton).setOnClickListener(v -> showContents());
        bookmarkButton.setOnClickListener(v -> toggleBookmark());
        pageInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        pageInput.setOnEditorActionListener((v, actionId, event) -> {
            goToInputPage(); return true;
        });
        pageInput.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) goToInputPage(); });

        executor.execute(() -> {
            try {
                File pdfFile = new File(getFilesDir(), "groen_op_de_balans.pdf");
                if (!pdfFile.exists() || pdfFile.length() < 1000000) {
                    try (InputStream in = getAssets().open("groen_op_de_balans.pdf"); FileOutputStream out = new FileOutputStream(pdfFile)) {
                        byte[] buffer = new byte[64 * 1024]; int n;
                        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                    }
                }
                descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                renderer = new PdfRenderer(descriptor);
                runOnUiThread(() -> showPage(0));
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Document kan niet worden geopend")
                    .setMessage(e.getClass().getSimpleName() + ": " + e.getMessage()).setPositiveButton("Sluiten", null).show());
            }
        });
    }

    private void goToInputPage() {
        if (renderer == null) return;
        try { showPage(Integer.parseInt(pageInput.getText().toString().trim()) - 1); }
        catch (Exception ignored) { pageInput.setText(String.valueOf(pageIndex + 1)); }
    }

    private void showPage(int requested) {
        if (renderer == null) return;
        int target = Math.max(0, Math.min(renderer.getPageCount() - 1, requested));
        pageIndex = target;
        progress.setVisibility(View.VISIBLE);
        pageTitle.setText("Pagina " + (target + 1) + " van " + renderer.getPageCount());
        pageInput.setText(String.valueOf(target + 1));
        updateBookmarkIcon();
        executor.execute(() -> {
            try (PdfRenderer.Page page = renderer.openPage(target)) {
                int maxWidth = Math.max(1080, getResources().getDisplayMetrics().widthPixels * 2);
                float factor = Math.min(3f, maxWidth / (float) page.getWidth());
                Bitmap bitmap = Bitmap.createBitmap(Math.max(1, (int)(page.getWidth() * factor)), Math.max(1, (int)(page.getHeight() * factor)), Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                runOnUiThread(() -> { if (pageIndex == target) pdfView.setBitmap(bitmap); else bitmap.recycle(); progress.setVisibility(View.GONE); });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Pagina kon niet worden geladen", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showContents() {
        String[] labels = new String[CHAPTERS.length + (bookmarks.isEmpty() ? 0 : 1)];
        int offset = 0;
        if (!bookmarks.isEmpty()) { labels[0] = "★ Bladwijzers"; offset = 1; }
        for (int i = 0; i < CHAPTERS.length; i++) labels[i + offset] = CHAPTERS[i].split("\\|")[0];
        final int finalOffset = offset;
        new AlertDialog.Builder(this).setTitle("Inhoud").setItems(labels, (dialog, which) -> {
            if (finalOffset == 1 && which == 0) { showBookmarks(); return; }
            int idx = which - finalOffset;
            showPage(Integer.parseInt(CHAPTERS[idx].split("\\|")[1]) - 1);
        }).setNegativeButton("Sluiten", null).show();
    }

    private void showBookmarks() {
        Integer[] pages = bookmarks.stream().map(Integer::parseInt).sorted().toArray(Integer[]::new);
        String[] labels = new String[pages.length];
        for (int i = 0; i < pages.length; i++) labels[i] = "Pagina " + (pages[i] + 1);
        new AlertDialog.Builder(this).setTitle("Bladwijzers").setItems(labels, (d, w) -> showPage(pages[w])).setNegativeButton("Sluiten", null).show();
    }

    private void toggleBookmark() {
        String key = String.valueOf(pageIndex);
        if (!bookmarks.add(key)) bookmarks.remove(key);
        getPreferences(MODE_PRIVATE).edit().putStringSet("bookmarks", new HashSet<>(bookmarks)).apply();
        updateBookmarkIcon();
    }

    private void updateBookmarkIcon() {
        bookmarkButton.setImageResource(bookmarks.contains(String.valueOf(pageIndex)) ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
        try { if (descriptor != null) descriptor.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
