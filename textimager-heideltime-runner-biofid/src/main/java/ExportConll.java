import de.unihd.dbs.uima.types.heideltime.Timex3;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.factory.UimaContextFactory;
import org.apache.uima.jcas.JCas;
import org.dkpro.core.io.xmi.XmiReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.uima.fit.util.JCasUtil.select;

/**
 *
 */
public class ExportConll {
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

            final CustomCoNLLWriter conllWriter = new CustomCoNLLWriter();
            final String outputPath = Paths.get(output_path).toAbsolutePath().toString();
            conllWriter.initialize(UimaContextFactory.createUimaContext(
                    CustomCoNLLWriter.PARAM_TARGET_LOCATION, outputPath,
                    CustomCoNLLWriter.PARAM_USE_DOCUMENT_ID, true,
                    CustomCoNLLWriter.PARAM_OVERWRITE, true,
                    CustomCoNLLWriter.PARAM_WRITE_COVERED_TEXT, true,
                    CustomCoNLLWriter.PARAM_WRITE_CHUNK, true,
                    CustomCoNLLWriter.PARAM_WRITE_NAMED_ENTITY, true
            ));

            JCas jCas = JCasFactory.createJCas();
            while (reader.hasNext()) {
                try {
                    reader.getNext(jCas.getCas());

                    removeIdentical(jCas);

                    conllWriter.process(jCas);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    jCas.reset();
                }
            }
        } catch (IOException | UIMAException e) {
            e.printStackTrace();
        }
    }

    public static void removeIdentical(JCas jCas) throws CASException {
        final JCas heidelTimeDefaultView = jCas.getView("heidelTimeDefault");
        final JCas heidelTimeBioFIDView = jCas.getView("heidelTimeBioFID");

        // Create an index of each Timex3 annotation for both HeideTime variant views,
        // using the span indices as keys for each annotation
        Map<IntTuple, Timex3> timex3MapDefault = select(heidelTimeDefaultView, Timex3.class)
                .stream()
                .collect(Collectors.toMap(
                        timex3 -> new IntTuple(timex3.getBegin(), timex3.getEnd()),
                        Function.identity()
                ));

        Map<IntTuple, Timex3> timex3MapBioFID = select(heidelTimeBioFIDView, Timex3.class)
                .stream()
                .collect(Collectors.toMap(
                        timex3 -> new IntTuple(timex3.getBegin(), timex3.getEnd()),
                        Function.identity()
                ));

        // Remove Timex3 annotations that cover the same span and have the same Timex3-type
        for (IntTuple key : timex3MapDefault.keySet()) {
            if (timex3MapBioFID.containsKey(key)) {
                Timex3 timex3Default = timex3MapDefault.get(key);
                Timex3 timex3BioFID = timex3MapBioFID.get(key);
                if (Objects.equals(timex3Default.getTimexType(), timex3BioFID.getTimexType())) {
                    heidelTimeDefaultView.removeFsFromIndexes(timex3Default);
                    heidelTimeBioFIDView.removeFsFromIndexes(timex3BioFID);
                }
            }
        }
    }


    /**
     * Basic tuple of two integer numbers.
     */
    private static class IntTuple {
        Integer left;
        Integer right;

        public IntTuple(int left, int right) {
            this.left = left;
            this.right = right;
        }

        /**
         * Returns true of both {@link #left} and {@link #right} values of this and {@link IntTuple another IntTuple} are equal.
         * @param other Another {@link IntTuple}
         * @return true of both {@link #left} and {@link #right} values of this and other are equal.
         */
        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IntTuple)) return false;
            IntTuple intTuple = (IntTuple) other;
            return Objects.equals(left, intTuple.left) && Objects.equals(right, intTuple.right);
        }

        @Override
        public int hashCode() {
            return Objects.hash(left, right);
        }
    }
}
