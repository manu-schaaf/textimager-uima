import com.google.common.collect.ImmutableList;
import de.unihd.dbs.uima.types.heideltime.Sentence;
import de.unihd.dbs.uima.types.heideltime.Timex3;
import de.unihd.dbs.uima.types.heideltime.Token;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.dkpro.core.io.xmi.XmiReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExportStats extends ExportConll {

    public static void main(String[] args) throws FileNotFoundException {
        if (args.length < 3) {
            throw new RuntimeException("Not enough arguments!");
        }

        String path = args[0];
        String pattern = args[1];
        String output_path = args[2];

        // Check if given path exists
        File filepath = Paths.get(path).toFile();
        if (!filepath.exists() || !filepath.isDirectory()) {
            throw new FileNotFoundException(String.format("The given path '%s' does not exist!", path));
        }
        try {
            final CollectionReader reader = CollectionReaderFactory.createReader(
                    XmiReader.class,
                    XmiReader.PARAM_SOURCE_LOCATION, path,
                    XmiReader.PARAM_PATTERNS, pattern,
                    XmiReader.PARAM_LANGUAGE, "de",
                    XmiReader.PARAM_LENIENT, true
            );
            CorpusStatistics corpusStats = new CorpusStatistics();
            Timex3Differences differenceBioFID = new Timex3Differences();
            Timex3Differences differenceDefault = new Timex3Differences();

            JCas jCas = JCasFactory.createJCas();
            while (reader.hasNext()) {
                reader.getNext(jCas.getCas());

                corpusStats.process(jCas);

                removeIdentical(jCas);
                differenceBioFID.process(jCas, "heidelTimeDefault", "heidelTimeBioFID");
                differenceDefault.process(jCas, "heidelTimeBioFID", "heidelTimeDefault");
            }

            System.out.println("Extended: " + differenceBioFID.extendedAnnotationCounter);
            System.out.println("Novel: " + differenceBioFID.novelAnnotationCounter);
            System.out.println("Reduced: " + differenceDefault.extendedAnnotationCounter);
            System.out.println("Missing: " + differenceDefault.novelAnnotationCounter);

            writeLines(output_path + "statistics.tsv", corpusStats.getStats());

            writeStats(output_path + "extended.tsv", differenceBioFID.extendedAnnotationCounter);
            writeStats(output_path + "novel.tsv", differenceBioFID.novelAnnotationCounter);
            writeStats(output_path + "partial.tsv", differenceDefault.extendedAnnotationCounter);
            writeStats(output_path + "missing.tsv", differenceDefault.novelAnnotationCounter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class CorpusStatistics {

        private final TreeSet<String> timexTypes = new TreeSet<>();
        private final HashMap<String, TreeCounter<String>> timexTypeCount = new HashMap<>();
        private final TreeSet<String> timexRules = new TreeSet<>();
        private final HashMap<String, TreeCounter<String>> timexRuleCount = new HashMap<>();
        private final TreeSet<Integer> timexLengths = new TreeSet<>();
        private final HashMap<String, TreeCounter<Integer>> timexLengthCount = new HashMap<>();
        private final Counter<String> einzelneNunCounter = new Counter<>();
        private final Counter<String> alleNunCounter = new Counter<>();


        private int sentenceCount = 0;
        private int tokenCount = 0;

        public CorpusStatistics() {
            for (String viewName : ImmutableList.of("heidelTimeDefault", "heidelTimeBioFID")) {
                this.timexTypeCount.put(viewName, new TreeCounter<>());
                this.timexRuleCount.put(viewName, new TreeCounter<>());
                this.timexLengthCount.put(viewName, new TreeCounter<>());
            }
        }

        public void process(JCas jCas) throws CASException {
            final JCas viewDefault = jCas.getView("heidelTimeDefault");
            sentenceCount += JCasUtil.select(viewDefault, Sentence.class).size();
            tokenCount += JCasUtil.select(viewDefault, Token.class).size();

            for (String viewName : ImmutableList.of("heidelTimeDefault", "heidelTimeBioFID")) {
                final JCas view = jCas.getView(viewName);
                Map<Timex3, Collection<Token>> timexTokens = JCasUtil.indexCovered(view, Timex3.class, Token.class);
                final Collection<Token> emptyList = new ArrayList<>();
                for (Timex3 timex3 : JCasUtil.select(view, Timex3.class)) {
                    int timexLength = timexTokens.getOrDefault(timex3, emptyList).size();
                    timexLengths.add(timexLength);
                    timexLengthCount.get(viewName).increment(timexLength);

                    String timexType = timex3.getTimexType();
                    timexTypes.add(timexType);
                    timexTypeCount.get(viewName).increment(timexType);

                    String timexRule = timex3.getFoundByRule();
                    timexRules.add(timexRule);
                    timexRuleCount.get(viewName).increment(timexRule);

                    if (timex3.getCoveredText().strip().equalsIgnoreCase("nun")) {
                        einzelneNunCounter.increment(viewName);
                    }
                    if (timex3.getCoveredText().strip().matches(".*\\b[Nn]un\\b.*")) {
                        alleNunCounter.increment(viewName);
                    }
                }
            }

        }

        public List<String> getStats() {
            ArrayList<String> stats = new ArrayList<>();
            stats.add("Field\tDefault\tExtended");
            stats.add(String.format("%s\t%d", "sentenceCount", this.sentenceCount));
            stats.add(String.format("%s\t%d", "tokenCount", this.tokenCount));

            stats.add(String.format(
                    "Timex3 matches: '^[Nu]un$'\t%d\t%d",
                    this.einzelneNunCounter.getOrDefault("heidelTimeDefault", 0),
                    this.einzelneNunCounter.getOrDefault("heidelTimeBioFID", 0)
            ));
            stats.add(String.format(
                    "Timex3 matches: '.*\\b[Nu]un\\b.*'\t%d\t%d",
                    this.alleNunCounter.getOrDefault("heidelTimeDefault", 0),
                    this.alleNunCounter.getOrDefault("heidelTimeBioFID", 0)
            ));

            Integer timexCountDefault = this.timexTypeCount.get("heidelTimeDefault").values().stream().reduce(Integer::sum).orElse(0);
            Integer timexCountBioFID = this.timexTypeCount.get("heidelTimeBioFID").values().stream().reduce(Integer::sum).orElse(0);
            stats.add(String.format("Timex3 of any type\t%d\t%d", timexCountDefault, timexCountBioFID));

            for (String key : this.timexTypes) {
                stats.add(String.format(
                        "Timex3 of type: %s\t%d\t%d",
                        key,
                        this.timexTypeCount.get("heidelTimeDefault").getOrDefault(key, 0),
                        this.timexTypeCount.get("heidelTimeBioFID").getOrDefault(key, 0)
                ));
            }
            for (String key : this.timexRules) {
                stats.add(String.format(
                        "Timex3 found by rule: %s\t%d\t%d",
                        key,
                        this.timexRuleCount.get("heidelTimeDefault").getOrDefault(key, 0),
                        this.timexRuleCount.get("heidelTimeBioFID").getOrDefault(key, 0)
                ));
            }
            for (Integer key : this.timexLengths) {
                stats.add(String.format(
                        "Timex3 covering %d tokens\t%d\t%d",
                        key,
                        this.timexLengthCount.get("heidelTimeDefault").getOrDefault(key, 0),
                        this.timexLengthCount.get("heidelTimeBioFID").getOrDefault(key, 0)
                ));
            }

            return stats;
        }
    }

    private static void writeStats(String fileName, Map<Iterable<String>, Integer> counter) throws FileNotFoundException {
        if (counter.size() <= 1)
            return;

        try (final PrintWriter printWriter = new PrintWriter(fileName)) {
            counter.forEach((annotation, count) -> {
                String annotationString = String.join("\t", annotation).replaceAll("\\n", "\\n");
                if (count == 0) {
                    printWriter.println(annotationString);
                } else {
                    printWriter.println(String.format("%s\t%s", annotationString, count));
                }
            });
        }
    }

    private static void writeLines(String fileName, List<String> lines) throws FileNotFoundException {
        try (final PrintWriter printWriter = new PrintWriter(fileName)) {
            lines.forEach(printWriter::println);
        }
    }

    private static class Timex3Differences {

        public final Counter<Iterable<String>> extendedAnnotationCounter = new Counter<>();
        public final Counter<Iterable<String>> novelAnnotationCounter = new Counter<>();

        public Timex3Differences() {
            this.extendedAnnotationCounter.put(
                    ImmutableList.of("Sequence", "Type", "Rule", "BaseSequence", "BaseType", "BaseRule", "Count"),
                    0
            );
            this.novelAnnotationCounter.put(
                    ImmutableList.of("Sequence", "Type", "Rule", "Count"),
                    0
            );
        }

        public void process(JCas jCas, String baseViewName, String modifiedViewName) throws CASException {
            final JCas baseView = jCas.getView(baseViewName);
            final JCas modifiedView = jCas.getView(modifiedViewName);

            final TreeMap<Integer, Timex3> beginMap = JCasUtil.select(baseView, Timex3.class)
                    .stream()
                    .collect(Collectors.toMap(
                            Timex3::getBegin,
                            Function.identity(),
                            (a, b) -> a,
                            TreeMap::new
                    ));

            final TreeMap<Integer, Timex3> endMap = JCasUtil.select(baseView, Timex3.class)
                    .stream()
                    .collect(Collectors.toMap(
                            Timex3::getEnd,
                            Function.identity(),
                            (a, b) -> a,
                            TreeMap::new
                    ));

            for (Timex3 timex3 : JCasUtil.select(modifiedView, Timex3.class)) {
                Timex3 beginFloorBegin = getValue(beginMap.floorEntry(timex3.getBegin()));
                Timex3 endCeilBegin = getValue(endMap.ceilingEntry(timex3.getBegin()));
                boolean beginInside = Objects.nonNull(beginFloorBegin) && Objects.equals(beginFloorBegin, endCeilBegin);

                Timex3 beginFloorEnd = getValue(beginMap.floorEntry(timex3.getEnd()));
                Timex3 endCeilEnd = getValue(endMap.ceilingEntry(timex3.getEnd()));
                boolean endInside = Objects.nonNull(beginFloorEnd) && Objects.equals(beginFloorEnd, endCeilEnd);

                Timex3 beginCeilBegin = getValue(beginMap.ceilingEntry(timex3.getBegin()));
                Timex3 endFloorEnd = getValue(endMap.floorEntry(timex3.getEnd()));
                boolean covers = Objects.nonNull(beginCeilBegin) && Objects.equals(beginCeilBegin, endFloorEnd);

                if ((beginInside ^ endInside) || covers) {
                    // Extended annotation
                    Timex3 baseAnnotation;
                    if (beginInside) baseAnnotation = beginFloorBegin;
                    else if (endInside) baseAnnotation = beginFloorEnd;
                    else baseAnnotation = beginCeilBegin;

                    extendedAnnotationCounter.increment(ImmutableList.of(
                            timex3.getCoveredText(),
                            timex3.getTimexType(),
                            timex3.getFoundByRule(),
                            baseAnnotation.getCoveredText(),
                            baseAnnotation.getTimexType(),
                            baseAnnotation.getFoundByRule()
                    ));
                } else {
                    // Novel annotation
                    novelAnnotationCounter.increment(ImmutableList.of(
                            timex3.getCoveredText(),
                            timex3.getTimexType(),
                            timex3.getFoundByRule()
                    ));
                }

            }
        }
    }

    private static Timex3 getValue(Map.Entry<Integer, Timex3> possiblyNull) {
        return possiblyNull == null ? null : possiblyNull.getValue();
    }


    private static class Counter<K> extends LinkedHashMap<K, Integer> {
        public void increment(K key) {
            this.put(key, this.getOrDefault(key, 0) + 1);
        }
    }

    private static class TreeCounter<K> extends TreeMap<K, Integer> {
        public void increment(K key) {
            this.put(key, this.getOrDefault(key, 0) + 1);
        }
    }
}
