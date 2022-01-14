#!/usr/bin/sh
OUTPUT_DIR='$HOME/Data/BIOfid/HeidelTime-eval';
CP_DEFAULT='textimager-heideltime-runner-default/target/lib/*:textimager-heideltime-runner-default/target/textimager-heideltime-runner-default-0.3.0.jar';
CP_BIOFID='textimager-heideltime-runner-biofid/target/lib/*:textimager-heideltime-runner-biofid/target/textimager-heideltime-runner-biofid-0.3.0.jar';

#java -cp "$CP_DEFAULT" RunHeidelTimeDefault $OUTPUT_DIR/Bundestag/ 'WP*/*.xml' $OUTPUT_DIR/Bundestag/output_default/
#java -cp "$CP_BIOFID" RunHeidelTimeBioFID $OUTPUT_DIR/Bundestag/output_default/ '*.xmi' $OUTPUT_DIR/Bundestag/output_biofid/
#java -cp "$CP_BIOFID" ExportConll $OUTPUT_DIR/Bundestag/output_biofid/ '*.xmi' $OUTPUT_DIR/Bundestag/output_conll/
java -cp "$CP_BIOFID" ExportStats $OUTPUT_DIR/Bundestag/output_biofid/ '*.xml' $OUTPUT_DIR/evaluation_bundestag_

#java -cp "$CP_DEFAULT" RunHeidelTimeDefault $OUTPUT_DIR/Zodobat/ '*.txt' $OUTPUT_DIR/Zodobat/output_default/
#java -cp "$CP_BIOFID" RunHeidelTimeBioFID $OUTPUT_DIR/Zodobat/output_default/ '*.xmi' $OUTPUT_DIR/Zodobat/output_biofid/
#java -cp "$CP_BIOFID" ExportConll $OUTPUT_DIR/Zodobat/output_biofid/ '*.xmi' $OUTPUT_DIR/Zodobat/output_conll/
java -cp "$CP_BIOFID" ExportStats $OUTPUT_DIR/Zodobat/output_biofid/ '*.xmi' $OUTPUT_DIR/evaluation_zodobat_

#java -cp "$CP_DEFAULT" RunHeidelTimeDefault $OUTPUT_DIR/DTA/ '*.xmi' $OUTPUT_DIR/DTA/output_default/
#java -cp "$CP_BIOFID" RunHeidelTimeBioFID $OUTPUT_DIR/DTA/output_default/ '*.xmi' $OUTPUT_DIR/DTA/output_biofid/
#java -cp "$CP_BIOFID" ExportConll $OUTPUT_DIR/DTA/output_biofid/ '*.xmi' $OUTPUT_DIR/DTA/output_conll/
java -cp "$CP_BIOFID" ExportStats $OUTPUT_DIR/DTA/output_biofid/ '*.xmi' $OUTPUT_DIR/evaluation_dta_

#java -cp "$CP_DEFAULT" RunHeidelTimeDefault $OUTPUT_DIR/SZ/ '*.tei' $OUTPUT_DIR/SZ/output_default/
#java -cp "$CP_BIOFID" RunHeidelTimeBioFID $OUTPUT_DIR/SZ/output_default/ '*.xmi' $OUTPUT_DIR/SZ/output_biofid/
#java -cp "$CP_BIOFID" ExportConll $OUTPUT_DIR/SZ/output_biofid/ '*.xmi' $OUTPUT_DIR/SZ/output_conll/
java -cp "$CP_BIOFID" ExportStats $OUTPUT_DIR/SZ/output_biofid/ '*.xmi' $OUTPUT_DIR/evaluation_sz_

#java -cp "$CP_DEFAULT" RunHeidelTimeDefaultLB $OUTPUT_DIR/Wikipedia/ '*.txt' $OUTPUT_DIR/Wikipedia/output_default/
#java -cp "$CP_BIOFID" RunHeidelTimeBioFID $OUTPUT_DIR/Wikipedia/output_default/ '*.xmi' $OUTPUT_DIR/Wikipedia/output_biofid/
#java -cp "$CP_BIOFID" ExportConll $OUTPUT_DIR/Wikipedia/output_biofid/ '*.xmi' $OUTPUT_DIR/Wikipedia/output_conll/
java -cp "$CP_BIOFID" ExportStats $OUTPUT_DIR/Wikipedia/output_biofid/ '*.xmi' $OUTPUT_DIR/evaluation_wikipedia_