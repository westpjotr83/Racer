package nl.groenstad.groenopdebalans;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
    private static final int SCREEN_HOME = 0;
    private static final int SCREEN_SEARCH = 1;
    private static final int SCREEN_READER = 2;

    private PdfRenderer renderer;
    private ParcelFileDescriptor descriptor;
    private PdfPageView pdfView;
    private TextView pageTitle;
    private TextView searchStatus;
    private EditText pageInput;
    private EditText searchInput;
    private ProgressBar progress;
    private ImageButton bookmarkButton;
    private LinearLayout benefitContainer;
    private Spinner categorySpinner;
    private View homeScreen;
    private View searchScreen;
    private View readerScreen;
    private Button navHome;
    private Button navSearch;
    private Button navReader;
    private Button continueButton;
    private ListView searchResults;

    private int pageIndex = 10;
    private int currentScreen = SCREEN_HOME;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger renderGeneration = new AtomicInteger();
    private Set<String> bookmarks;
    private final List<PageText> pageTexts = new ArrayList<>();
    private boolean searchIndexReady = false;
    private SearchHitAdapter searchAdapter;

    private static final Chapter[] CHAPTERS = {
        new Chapter("Voorwoord", 9),
        new Chapter("1. Het Versteende Tijdperk", 11),
        new Chapter("2. De Groene Stad als innovatieve hot spot", 21),
        new Chapter("3. Van norm naar waarde", 29),
        new Chapter("4. Aangenaam verpozen", 37),
        new Chapter("5. Groen vast goed", 45),
        new Chapter("6. Eten wat de stad schaft", 55),
        new Chapter("7. De rijkdom van de rustplaats", 63),
        new Chapter("8. Mensenrijk, dierenrijk en plantenrijk", 71),
        new Chapter("9. Aan de straatstenen niet kwijt", 79),
        new Chapter("10. Werk aan de winkel", 89),
        new Chapter("11. Wat heet warm", 97),
        new Chapter("12. Over groene longen en leven van de lucht", 105),
        new Chapter("13. Van kantoortuin tot groen kantoor tot groene werkplaats", 115),
        new Chapter("14. Kom je buiten spelen?", 123),
        new Chapter("15. Van zorg voor meer groen tot minder zorg door meer groen", 133),
        new Chapter("16. Over nieuw eigenaarschap en nieuwe collectiviteit", 143),
        new Chapter("17. Epiloog: De balans opgemaakt", 149)
    };

    private static final Insight[] INSIGHTS = {
        new Insight("Klimaat", "€1 mld/jaar", "Bomen dempen geluidsoverlast", "Een investering in 10 miljoen bomen kan volgens het boek binnen vijf jaar worden terugverdiend.", 105),
        new Insight("Gezondheid", "€5–50 mln/jaar", "Volkstuinen zijn gezond", "10.000 nieuwe volkstuinen kunnen duizenden ziekenhuisdagen en zorgkosten vermijden.", 55),
        new Insight("Water", "€420.000/jaar", "Bomen houden water vast", "100.000 bomen creëren maatschappelijke baten door extra waterberging.", 79),
        new Insight("Water", "€16–32 mln/jaar", "Groene daken zuiveren water", "21 miljoen m² groen dak verlaagt volgens de boekberekening de zuiveringskosten.", 79),
        new Insight("Water", "€28 mln/jaar", "Groene daken beperken waterschade", "Een miljoen m² groen dak kan een deel van de schade door wateroverlast voorkomen.", 79),
        new Insight("Economie", "Tot 15%", "Groen verhoogt productiviteit", "Werknemers in een groene omgeving zijn volgens de aangehaalde studies productiever.", 115),
        new Insight("Water", "€28 mln/jaar", "Bedrijventerreinen als spons", "Groene daken en wanden op bedrijventerreinen kunnen wateroverlast verminderen.", 89),
        new Insight("Klimaat", "87,5 mln kg CO₂", "Meer bomen leggen CO₂ vast", "Een groei van het Nederlandse bomenbestand met 1% levert extra jaarlijkse vastlegging op.", 97),
        new Insight("Klimaat", "50 mln ton CO₂", "Flats als verticale bossen", "Groene hoogbouw kan op grote schaal bijdragen aan CO₂-vastlegging en leefkwaliteit.", 115),
        new Insight("Gezondheid", "€152 mln", "Groen zuivert de lucht", "De regulatiefunctie van natuur voor fijnstof vertegenwoordigt een substantiële waarde.", 105),
        new Insight("Gezondheid", "€23 mld", "Groen helpt overgewicht voorkomen", "In groene wijken komt volgens het boek minder overgewicht voor.", 133),
        new Insight("Gezondheid", "€600 mln/jaar", "Groen als medicijn", "Meer groen kan de kosten van longziekten helpen terugdringen.", 133),
        new Insight("Gezondheid", "€220 mln", "Groen helpt ADHD-kosten beperken", "Groene wijken kunnen volgens de publicatie medicatiekosten helpen verminderen.", 123),
        new Insight("Gezondheid", "€1,3 mld/jaar", "Groen remt zorgkostengroei", "Een beperkte afname van de groei van zorgkosten heeft al een groot financieel effect.", 133),
        new Insight("Gezondheid", "€486–780 mln/jaar", "Groen verkort ziekenhuisopnames", "Vergroening rond ziekenhuizen kan bijdragen aan kortere opnameduur.", 133),
        new Insight("Economie", "€5–20 mln/jaar", "Groene steden trekken toeristen", "Een aantrekkelijke groene stad levert extra recreatieve en toeristische bestedingen op.", 37),
        new Insight("Natuur", "€40/ha", "Parken zijn groene parels", "Parken hebben volgens de aangehaalde CBS-benadering een hoge monetaire waarde per hectare.", 37),
        new Insight("Klimaat", "20.000 ton CO₂", "Begraafplaatsen als groene erfenis", "Het halfopen landschap van begraafplaatsen legt jaarlijks CO₂ vast.", 63),
        new Insight("Gezondheid", "120 min/week", "Groen draagt bij aan geluk", "Regelmatig verblijf in de natuur hangt samen met een betere ervaren gezondheid en geluk.", 37),
        new Insight("Economie", "€3,7 mld/jaar", "Groen is een groot feest", "Parken en recreatiegebieden dragen een aanzienlijk deel van de festivaleconomie.", 37),
        new Insight("Wonen", "€28 mln/jaar", "Groene woningbouw verhoogt OZB", "Een groene invulling van 900.000 woningen levert gemeenten extra belastingopbrengsten op.", 45),
        new Insight("Wonen", "Rapportcijfer 8,9", "De gelukkigste wijk is groen", "De groene wijk Alteveer-Cranevelt scoort zeer hoog op tevredenheid.", 45),
        new Insight("Natuur", "Minder plaagschade", "Biodiversiteit brengt balans", "Hoge biodiversiteit helpt ongewenste plaagvorming en maatschappelijke schade voorkomen.", 71)
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setStatusBarColor(Color.rgb(18, 61, 42));
        getWindow().setNavigationBarColor(Color.rgb(247, 250, 246));
        if (Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        bindViews();
        applySystemInsets();
        setupNavigation();
        setupReaderControls();
        setupHome();
        setupSearch();

        bookmarks = new HashSet<>(getPreferences(MODE_PRIVATE).getStringSet("bookmarks", new HashSet<>()));
        pageIndex = getPreferences(MODE_PRIVATE).getInt("lastPage", 10);
        switchScreen(SCREEN_HOME);
        loadDocumentAndSearchIndex();
    }

    private void bindViews() {
        pdfView = findViewById(R.id.pdfView);
        pageTitle = findViewById(R.id.pageTitle);
        searchStatus = findViewById(R.id.searchStatus);
        pageInput = findViewById(R.id.pageInput);
        searchInput = findViewById(R.id.searchInput);
        progress = findViewById(R.id.progress);
        bookmarkButton = findViewById(R.id.bookmarkButton);
        benefitContainer = findViewById(R.id.benefitContainer);
        categorySpinner = findViewById(R.id.categorySpinner);
        homeScreen = findViewById(R.id.homeScreen);
        searchScreen = findViewById(R.id.searchScreen);
        readerScreen = findViewById(R.id.readerScreen);
        navHome = findViewById(R.id.navHome);
        navSearch = findViewById(R.id.navSearch);
        navReader = findViewById(R.id.navReader);
        continueButton = findViewById(R.id.continueButton);
        searchResults = findViewById(R.id.searchResults);
        findViewById(R.id.menuButton).setOnClickListener(v -> showMainMenu());
        bookmarkButton.setOnClickListener(v -> toggleBookmark());
    }

    private void applySystemInsets() {
        View root = findViewById(R.id.root);
        View topBar = findViewById(R.id.topBar);
        View bottomNav = findViewById(R.id.bottomNav);
        View contentHost = findViewById(R.id.contentHost);

        final int topL = topBar.getPaddingLeft();
        final int topT = topBar.getPaddingTop();
        final int topR = topBar.getPaddingRight();
        final int topB = topBar.getPaddingBottom();
        final int navL = bottomNav.getPaddingLeft();
        final int navT = bottomNav.getPaddingTop();
        final int navR = bottomNav.getPaddingRight();
        final int navB = bottomNav.getPaddingBottom();

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            topBar.setPadding(topL + left, topT + top, topR + right, topB);
            bottomNav.setPadding(navL + left, navT, navR + right, navB + bottom);
            contentHost.setPadding(left, 0, right, 0);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void setupNavigation() {
        navHome.setOnClickListener(v -> switchScreen(SCREEN_HOME));
        navSearch.setOnClickListener(v -> switchScreen(SCREEN_SEARCH));
        navReader.setOnClickListener(v -> switchScreen(SCREEN_READER));
    }

    private void switchScreen(int screen) {
        currentScreen = screen;
        homeScreen.setVisibility(screen == SCREEN_HOME ? View.VISIBLE : View.GONE);
        searchScreen.setVisibility(screen == SCREEN_SEARCH ? View.VISIBLE : View.GONE);
        readerScreen.setVisibility(screen == SCREEN_READER ? View.VISIBLE : View.GONE);
        bookmarkButton.setVisibility(screen == SCREEN_READER ? View.VISIBLE : View.GONE);
        setNavSelected(navHome, screen == SCREEN_HOME);
        setNavSelected(navSearch, screen == SCREEN_SEARCH);
        setNavSelected(navReader, screen == SCREEN_READER);

        if (screen == SCREEN_HOME) pageTitle.setText("Ontdek de maatschappelijke waarde van groen");
        else if (screen == SCREEN_SEARCH) pageTitle.setText("Volledige offline zoekfunctie");
        else updatePageHeader();
    }

    private void setNavSelected(Button button, boolean selected) {
        button.setTextColor(selected ? Color.WHITE : Color.rgb(18, 61, 42));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(selected ? Color.rgb(47, 125, 74) : Color.TRANSPARENT);
        button.setBackground(bg);
        button.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void setupReaderControls() {
        findViewById(R.id.previousButton).setOnClickListener(v -> showPage(pageIndex - 1));
        findViewById(R.id.nextButton).setOnClickListener(v -> showPage(pageIndex + 1));
        pageInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        pageInput.setOnEditorActionListener((v, actionId, event) -> {
            goToInputPage();
            return true;
        });
        pageInput.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) goToInputPage(); });
        pdfView.setPageSwipeListener(new PdfPageView.PageSwipeListener() {
            @Override public void onNextPage() { showPage(pageIndex + 1); }
            @Override public void onPreviousPage() { showPage(pageIndex - 1); }
        });
    }

    private void setupHome() {
        String[] categories = {"Alle thema's", "Gezondheid", "Water", "Klimaat", "Economie", "Wonen", "Natuur"};
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories);
        categorySpinner.setAdapter(categoryAdapter);
        categorySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                renderInsights(categories[position]);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        continueButton.setOnClickListener(v -> openReaderAt(pageIndex));
        findViewById(R.id.calculatorButton).setOnClickListener(v -> showCalculator());
        renderInsights("Alle thema's");
    }

    private void renderInsights(String category) {
        benefitContainer.removeAllViews();
        for (Insight insight : INSIGHTS) {
            if (!"Alle thema's".equals(category) && !category.equals(insight.category)) continue;
            TextView card = new TextView(this);
            card.setText(insight.metric + "  ·  " + insight.category + "\n" + insight.title + "\n" + insight.summary);
            card.setTextColor(Color.rgb(35, 55, 43));
            card.setTextSize(15f);
            card.setLineSpacing(0f, 1.12f);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setGravity(Gravity.START);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(16));
            bg.setStroke(dp(1), Color.rgb(217, 226, 216));
            card.setBackground(bg);
            card.setElevation(dp(2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            card.setLayoutParams(lp);
            card.setOnClickListener(v -> openReaderAt(pdfIndexForBookPage(insight.bookPage)));
            benefitContainer.addView(card);
        }
    }

    private void setupSearch() {
        searchAdapter = new SearchHitAdapter();
        searchResults.setAdapter(searchAdapter);
        searchResults.setOnItemClickListener((parent, view, position, id) -> openReaderAt(searchAdapter.getItem(position).pageIndex));
        findViewById(R.id.searchButton).setOnClickListener(v -> runSearch());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            runSearch();
            return true;
        });
    }

    private void loadDocumentAndSearchIndex() {
        progress.setVisibility(View.VISIBLE);
        ioExecutor.execute(() -> {
            try {
                File pdfFile = new File(getFilesDir(), "groen_op_de_balans.pdf");
                if (!pdfFile.exists() || pdfFile.length() < 10_000_000) {
                    try (InputStream in = getAssets().open("groen_op_de_balans.pdf"); FileOutputStream out = new FileOutputStream(pdfFile)) {
                        byte[] buffer = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                    }
                }
                descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                renderer = new PdfRenderer(descriptor);
                loadSearchAsset();
                runOnUiThread(() -> {
                    pageIndex = Math.max(0, Math.min(renderer.getPageCount() - 1, pageIndex));
                    progress.setVisibility(View.GONE);
                    updateContinueButton();
                    showPage(pageIndex);
                    switchScreen(currentScreen);
                });
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("Document kan niet worden geopend")
                    .setMessage(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .setPositiveButton("Sluiten", null)
                    .show());
            }
        });
    }

    private void loadSearchAsset() throws Exception {
        try (InputStream in = getAssets().open("search_index.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[32 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            JSONArray pages = new JSONArray(out.toString(StandardCharsets.UTF_8));
            synchronized (pageTexts) {
                pageTexts.clear();
                for (int i = 0; i < pages.length(); i++) {
                    JSONObject item = pages.getJSONObject(i);
                    pageTexts.add(new PageText(item.getInt("page"), item.getString("text")));
                }
            }
            searchIndexReady = true;
            runOnUiThread(() -> searchStatus.setText(pageTexts.size() + " pagina's lokaal geïndexeerd"));
        }
    }

    private void runSearch() {
        String query = clean(searchInput.getText().toString());
        if (query.length() < 2) {
            Toast.makeText(this, "Voer minimaal twee tekens in", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!searchIndexReady) {
            Toast.makeText(this, "De zoekindex wordt nog geladen", Toast.LENGTH_SHORT).show();
            return;
        }
        searchStatus.setText("Zoeken…");
        searchAdapter.setHits(Collections.emptyList());
        searchExecutor.execute(() -> {
            String[] terms = query.split("\\s+");
            List<SearchHit> hits = new ArrayList<>();
            synchronized (pageTexts) {
                for (PageText page : pageTexts) {
                    String lower = page.text.toLowerCase(Locale.ROOT);
                    boolean all = true;
                    int first = Integer.MAX_VALUE;
                    int score = lower.contains(query) ? 100 : 0;
                    for (String term : terms) {
                        int at = lower.indexOf(term);
                        if (at < 0) { all = false; break; }
                        first = Math.min(first, at);
                        score += countOccurrences(lower, term);
                    }
                    if (all) hits.add(new SearchHit(page.pageIndex, makeSnippet(page.text, first), score));
                }
            }
            hits.sort(Comparator.comparingInt((SearchHit h) -> h.score).reversed().thenComparingInt(h -> h.pageIndex));
            if (hits.size() > 80) hits = new ArrayList<>(hits.subList(0, 80));
            List<SearchHit> finalHits = hits;
            runOnUiThread(() -> {
                searchAdapter.setHits(finalHits);
                searchStatus.setText(finalHits.isEmpty() ? "Geen resultaten" : finalHits.size() + " resultaten · tik om te openen");
            });
        });
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) {
            count++;
            at += Math.max(1, needle.length());
        }
        return count;
    }

    private static String makeSnippet(String text, int index) {
        if (text.isEmpty()) return "";
        int start = Math.max(0, index - 90);
        int end = Math.min(text.length(), index + 230);
        while (start > 0 && start < text.length() && Character.isLetterOrDigit(text.charAt(start))) start--;
        while (end < text.length() && Character.isLetterOrDigit(text.charAt(end - 1))) end++;
        return (start > 0 ? "…" : "") + text.substring(start, Math.min(end, text.length())).trim() + (end < text.length() ? "…" : "");
    }

    private static String clean(String value) {
        return value.toLowerCase(Locale.ROOT).replace('\u00ad', ' ').replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }

    private void goToInputPage() {
        if (renderer == null || pageInput.getText().toString().trim().isEmpty()) return;
        try {
            int bookPage = Integer.parseInt(pageInput.getText().toString().trim());
            int maxBookPage = renderer.getPageCount() - 2;
            if (bookPage < 6 || bookPage > maxBookPage) throw new NumberFormatException();
            showPage(pdfIndexForBookPage(bookPage));
        } catch (Exception ignored) {
            Toast.makeText(this, "Gebruik een boekpagina tussen 6 en " + (renderer.getPageCount() - 2), Toast.LENGTH_SHORT).show();
            updatePageHeader();
        }
    }

    private void openReaderAt(int target) {
        switchScreen(SCREEN_READER);
        showPage(target);
    }

    private void showPage(int requested) {
        if (renderer == null) return;
        int target = Math.max(0, Math.min(renderer.getPageCount() - 1, requested));
        pageIndex = target;
        getPreferences(MODE_PRIVATE).edit().putInt("lastPage", pageIndex).apply();
        updatePageHeader();
        updateBookmarkIcon();
        updateContinueButton();
        progress.setVisibility(View.VISIBLE);
        int generation = renderGeneration.incrementAndGet();
        ioExecutor.execute(() -> {
            try (PdfRenderer.Page page = renderer.openPage(target)) {
                int targetWidth = Math.max(1440, getResources().getDisplayMetrics().widthPixels * 2);
                float factor = Math.min(3f, targetWidth / (float) page.getWidth());
                Bitmap bitmap = Bitmap.createBitmap(
                    Math.max(1, (int) (page.getWidth() * factor)),
                    Math.max(1, (int) (page.getHeight() * factor)),
                    Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                runOnUiThread(() -> {
                    if (renderGeneration.get() == generation && pageIndex == target) pdfView.setBitmap(bitmap);
                    else if (!bitmap.isRecycled()) bitmap.recycle();
                    progress.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Pagina kon niet worden geladen", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void updatePageHeader() {
        if (renderer == null) return;
        int bookPage = bookPageForPdfIndex(pageIndex);
        String chapter = currentChapterTitle(pageIndex);
        if (bookPage >= 6) {
            pageTitle.setText(chapter + " · boekpagina " + bookPage + " · PDF " + (pageIndex + 1) + "/" + renderer.getPageCount());
            pageInput.setText(String.valueOf(bookPage));
        } else {
            pageTitle.setText("Voorwerk · PDF-pagina " + (pageIndex + 1) + "/" + renderer.getPageCount());
            pageInput.setText("");
        }
    }

    private void updateContinueButton() {
        int bookPage = bookPageForPdfIndex(pageIndex);
        continueButton.setText(bookPage >= 6 ? "Verder lezen · boekpagina " + bookPage : "Verder lezen");
    }

    private String currentChapterTitle(int pdfIndex) {
        String result = "Voorwerk";
        for (Chapter chapter : CHAPTERS) {
            if (pdfIndex >= pdfIndexForBookPage(chapter.bookPage)) result = chapter.title;
            else break;
        }
        return result;
    }

    private static int pdfIndexForBookPage(int bookPage) {
        return bookPage + 1;
    }

    private static int bookPageForPdfIndex(int pdfIndex) {
        return pdfIndex >= 7 ? pdfIndex - 1 : -1;
    }

    private void showMainMenu() {
        String[] options = {"Inhoudsopgave", "Bladwijzers", "Maatschappelijke baten", "Groenrekentool", "Over deze app"};
        new AlertDialog.Builder(this).setTitle("Menu").setItems(options, (dialog, which) -> {
            if (which == 0) showContents();
            else if (which == 1) showBookmarks();
            else if (which == 2) switchScreen(SCREEN_HOME);
            else if (which == 3) showCalculator();
            else showAbout();
        }).setNegativeButton("Sluiten", null).show();
    }

    private void showContents() {
        String[] labels = new String[CHAPTERS.length];
        for (int i = 0; i < CHAPTERS.length; i++) labels[i] = CHAPTERS[i].title + "  ·  p. " + CHAPTERS[i].bookPage;
        new AlertDialog.Builder(this).setTitle("Inhoudsopgave").setItems(labels, (dialog, which) -> openReaderAt(pdfIndexForBookPage(CHAPTERS[which].bookPage))).setNegativeButton("Sluiten", null).show();
    }

    private void showBookmarks() {
        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "Nog geen bladwijzers", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Integer> pages = new ArrayList<>();
        for (String value : bookmarks) pages.add(Integer.parseInt(value));
        Collections.sort(pages);
        String[] labels = new String[pages.size()];
        for (int i = 0; i < pages.size(); i++) {
            int bookPage = bookPageForPdfIndex(pages.get(i));
            labels[i] = bookPage >= 6 ? currentChapterTitle(pages.get(i)) + " · boekpagina " + bookPage : "PDF-pagina " + (pages.get(i) + 1);
        }
        new AlertDialog.Builder(this).setTitle("Bladwijzers").setItems(labels, (d, w) -> openReaderAt(pages.get(w))).setNegativeButton("Sluiten", null).show();
    }

    private void toggleBookmark() {
        String key = String.valueOf(pageIndex);
        boolean added = bookmarks.add(key);
        if (!added) bookmarks.remove(key);
        getPreferences(MODE_PRIVATE).edit().putStringSet("bookmarks", new HashSet<>(bookmarks)).apply();
        updateBookmarkIcon();
        Toast.makeText(this, added ? "Bladwijzer opgeslagen" : "Bladwijzer verwijderd", Toast.LENGTH_SHORT).show();
    }

    private void updateBookmarkIcon() {
        bookmarkButton.setImageResource(bookmarks.contains(String.valueOf(pageIndex)) ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
    }

    private void showCalculator() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        Spinner type = new Spinner(this);
        String[] types = {"Bomen: waarde waterberging", "Groen dak: waterzuivering", "Groen dak: vermeden waterschade"};
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types));
        EditText amount = new EditText(this);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setHint("Aantal bomen of m²");
        TextView result = new TextView(this);
        result.setTextSize(18f);
        result.setTextColor(Color.rgb(18, 61, 42));
        result.setPadding(0, dp(12), 0, dp(8));
        TextView note = new TextView(this);
        note.setText("Indicatieve lineaire rekensom op basis van de kengetallen in het boek. Geen vervanging voor een lokale businesscase.");
        note.setTextSize(12f);
        note.setTextColor(Color.DKGRAY);
        box.addView(type, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        box.addView(amount, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        box.addView(result);
        box.addView(note);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Groenrekentool")
            .setView(box)
            .setPositiveButton("Bereken", null)
            .setNegativeButton("Sluiten", null)
            .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                double value = Double.parseDouble(amount.getText().toString().replace(',', '.'));
                NumberFormat euro = NumberFormat.getCurrencyInstance(new Locale("nl", "NL"));
                if (type.getSelectedItemPosition() == 0) {
                    result.setText("Indicatieve jaarlijkse baat: " + euro.format(value * 4.2));
                } else if (type.getSelectedItemPosition() == 1) {
                    result.setText("Indicatieve jaarlijkse bandbreedte: " + euro.format(value * (16_000_000d / 21_000_000d)) + " – " + euro.format(value * (32_000_000d / 21_000_000d)));
                } else {
                    result.setText("Indicatief vermeden schadebedrag: " + euro.format(value * 28d) + " per jaar");
                }
            } catch (Exception e) {
                result.setText("Voer een geldig aantal in.");
            }
        }));
        dialog.show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
            .setTitle("Groen op de Balans · versie 3.0")
            .setMessage("Volledig offline kennis- en leesapp op basis van de publicatie Groen op de Balans.\n\nFuncties: volledige tekstzoekfunctie, juiste hoofdstuknavigatie, 23 maatschappelijke baten, rekentool, bladwijzers, zoom en veegbediening.\n\nDe app vraagt geen internet- of andere gevoelige Android-permissies. Zoekgegevens, leespositie en bladwijzers blijven lokaal op het toestel.")
            .setPositiveButton("Sluiten", null)
            .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        renderGeneration.incrementAndGet();
        ioExecutor.shutdownNow();
        searchExecutor.shutdownNow();
        try { if (renderer != null) renderer.close(); } catch (Exception ignored) { }
        try { if (descriptor != null) descriptor.close(); } catch (Exception ignored) { }
        super.onDestroy();
    }

    private static final class Chapter {
        final String title;
        final int bookPage;
        Chapter(String title, int bookPage) { this.title = title; this.bookPage = bookPage; }
    }

    private static final class Insight {
        final String category;
        final String metric;
        final String title;
        final String summary;
        final int bookPage;
        Insight(String category, String metric, String title, String summary, int bookPage) {
            this.category = category;
            this.metric = metric;
            this.title = title;
            this.summary = summary;
            this.bookPage = bookPage;
        }
    }

    private static final class PageText {
        final int pageIndex;
        final String text;
        PageText(int pageIndex, String text) { this.pageIndex = pageIndex; this.text = text; }
    }

    private static final class SearchHit {
        final int pageIndex;
        final String snippet;
        final int score;
        SearchHit(int pageIndex, String snippet, int score) { this.pageIndex = pageIndex; this.snippet = snippet; this.score = score; }
    }

    private final class SearchHitAdapter extends BaseAdapter {
        private List<SearchHit> hits = new ArrayList<>();

        void setHits(List<SearchHit> newHits) {
            hits = new ArrayList<>(newHits);
            notifyDataSetChanged();
        }

        @Override public int getCount() { return hits.size(); }
        @Override public SearchHit getItem(int position) { return hits.get(position); }
        @Override public long getItemId(int position) { return hits.get(position).pageIndex; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView heading;
            TextView body;
            if (convertView == null) {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(12), dp(11), dp(12), dp(11));
                heading = new TextView(MainActivity.this);
                heading.setTextColor(Color.rgb(18, 61, 42));
                heading.setTextSize(15f);
                heading.setTypeface(null, Typeface.BOLD);
                body = new TextView(MainActivity.this);
                body.setTextColor(Color.rgb(66, 82, 72));
                body.setTextSize(13.5f);
                body.setLineSpacing(0f, 1.08f);
                body.setPadding(0, dp(4), 0, 0);
                row.addView(heading);
                row.addView(body);
                row.setTag(new ViewHolder(heading, body));
            } else {
                row = (LinearLayout) convertView;
                ViewHolder holder = (ViewHolder) row.getTag();
                heading = holder.heading;
                body = holder.body;
            }
            SearchHit hit = getItem(position);
            int bookPage = bookPageForPdfIndex(hit.pageIndex);
            heading.setText((bookPage >= 6 ? "Boekpagina " + bookPage : "PDF-pagina " + (hit.pageIndex + 1)) + " · " + currentChapterTitle(hit.pageIndex));
            body.setText(hit.snippet);
            return row;
        }
    }

    private static final class ViewHolder {
        final TextView heading;
        final TextView body;
        ViewHolder(TextView heading, TextView body) { this.heading = heading; this.body = body; }
    }
}
