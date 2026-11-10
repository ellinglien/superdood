#!/usr/bin/env bash
# Desktop checks for the Inkwash tools. scripts/test_document.sh runs these too.
set -euo pipefail
cd "$(dirname "$0")/.."
# The app ships the notices as they are in the repo.
cmp -s THIRD_PARTY_NOTICES.md app/src/main/assets/THIRD_PARTY_NOTICES.txt || { echo "FAIL: THIRD_PARTY_NOTICES.md and app/src/main/assets/THIRD_PARTY_NOTICES.txt differ"; exit 1; }
# The built brush-lab pages are not tracked (2026-10-27); VoiceChecks and BrushNamesChecks read them, so build them first.
node docs/brush-lab/studio-src/build.cjs >/dev/null && node docs/brush-lab/match-lab-src/build.js >/dev/null
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
main=app/src/main/java/io/github/mpdairy/monopaint
test=app/src/test/java/io/github/mpdairy/monopaint
sources=(SecondInk PageWriting WritingLines Transcript AppName LassoRender ShapeStroke ShapeGeometry DirtyRegions LassoPieces AppRename HandOver Assets Cores TypeStyle BrushNames PaperNoise PaperGrain SurfaceGrain BrushTexture BrushSetting BrushStyle BrushCode Brush BrushCaptions BrushLibrary LibraryCleanup BrushBackup ToolBackup BrushImport ToolSwatches ToolSlots FingerTool BrushLab LabPages LabText LabLayout DrawLayout ToolPresets ToolsLayout ToolsPage ToolKits GrayPalette DotPattern ToneDocument DocumentCodec NotebookLayout NotebookExport NotebookPdf NotebookMigration DrawingBook PackedPage BookCodec BookPictures PagePicture RecoveryCodec RecoverySchedule LayerThumbnail BrushDirection ToolSettings DrawingStroke LiftingStroke DrawingFiles InkWashLayers InkWashSizes InkWashShades PenSpeed RoundFootprint WashCoverage PageTilt GravityFlow DryingWater SettlePacer SettleWorker WetWorkBudget BrushInk PaintLoad WetWatercolor WetCanvas WatercolorSession WetPaintStroke EraseBothStroke EraseKind DrawingNames WildBrush WildSample BrushPart PartDial PartChoice ModularFeatures ModularStyle ModularLocks ModularCode ModularBrush ModularVariations ModularWords BrushVoice ResolvedBrush FloatTiles StrokePoints StrokeMath StrokePaper StickGrain LineStamper Placement HakeRibbon BodyStamper BodySettle ModularCompose StrokeTones ModularStroke ModularSizes LegacyBrushes Prof PenBatch FootprintSweep QuickStroke CarryPlane Carry FloatBath FloatScan FloatTrace FloatPen FloatWater BristleSwarm StippleStamper HatchStamper HandHatchStamper RulingPen TwigStick FanBrush Brayer HeldPen SteadyHand DisplayRecovery ToneDabs ToolStroke HaloGrowth Magnets TextBoxes MagnetDrag WordPacks WordBinLayout PageShade BlankPages NotebookNames PageClear PrintScreen CoverNoise CoverMarble CoverDesign CoverPattern CoverTexture NotebookCovers BookOptions NotebookTemplate NotebookOptions SurpriseTools PinchPan PageView CanvasViewport MultiFingerTap FillRegion FillTool HeldPaint ClearWater Mirrors MirrorStroke MirrorShow ShadeStrip SwatchSetLayout FlickFling LoadedPaint PageClock NotebookRules TimedPages SandTimer FadingInk LaterMedia StampNib StampStamper Crayon SpiroStamper SwatchPicker StayIn FilledShape GreyGradient StrokeLog StripGesture TracingPaper GhostPlane CoverWear PageStacks LayerEdits LibraryCard CardArt CardSheet TimedSections PictureImport HoverNib FloatingSelection LassoPlanes EdgeHold PageExport PsdWriter HoldSnap LiveChange LiveOwn LiveReveal LiveTools StrokeStart LiveHost LiveSend LiveCrypt LivePresence LivePage LiveBooks QrCode NotebookStrip WetLook ReplayRun SystemSettings ZenFan VectorLines VectorInk FineGrid SharpStrokes SharpView ExportSize PngRender Fridge)
checks=(SecondInkOptionChecks SecondInkCopyChecks AppRenameChecks LassoRenderChecks HandOverChecks KindVisibilityChecks ToolBackupChecks RoundFootprintChecks EdgeRippleChecks LinkedEditChecks InkWashLayersChecks LayerPairChecks LayerThumbnailChecks InkWashSizesChecks InkWashShadesChecks WashCoverageChecks EraseBothChecks PenSpeedChecks DrawingNamesChecks DrawingRenameChecks WildBrushChecks WildSampleChecks BrushNamesChecks BrushTextureChecks BrushCodeChecks BrushChecks BrushLibraryChecks LibraryCleanupChecks ToolSlotsChecks ToolSwatchesChecks BrushLabChecks LabSlotsChecks LabLayoutChecks PartModelChecks ModularStyleChecks ModularCodeChecks ModularVariationsChecks ResolveChecks StrokeBasicsChecks LineStamperChecks BodyStamperChecks ModularStrokeChecks FastWetChecks ComposeShortcutChecks ModularBenchChecks LatticeChecks ModularBrushChecks ModularLabChecks DryingWaterChecks SettlePacingChecks SettleWorkerChecks GravityChecks PartSampleChecks LegacyConversionChecks DrawLayoutChecks FingerToolChecks ToolPresetsChecks ToolsPageChecks ToolKitsChecks VoiceChecks CoresChecks QuickStrokeChecks CarrySmudgeChecks FloatChecks BristleSwarmChecks ErasableChecks EraseKindChecks KindLayersChecks EraseAllChecks StippleChecks HatchChecks HandHatchChecks NibChecks PreviewWidthChecks ScreenFillChecks BlendChecks DisplayRecoveryChecks StumpChecks ResistChecks HaloChecks MagnetChecks TextBoxChecks WordPacksChecks WordBinChecks NotebookFileChecks NotebookMigrationChecks NotebookExportChecks NotebookPdfChecks NotebookLayoutChecks NotebookPagesChecks SaveLessChecks PageShadeChecks PageColourChecks BlankPagesChecks NotebookNamesChecks PageClearChecks ScreenChecks CoverChecks CoverDesignChecks NotebookOptionsChecks ZoomStrokeChecks PinchPanChecks FillRegionChecks FillChecks WetSmudgeChecks PressSmudgeChecks MirrorChecks FillMirrorChecks GreyBarChecks FlickFlingChecks OilChecks SmudgeSizeChecks TrailChecks NotebookRulesChecks SandTimerChecks FadingChecks StampChecks CrayonChecks SpiroChecks SwatchChecks StayInChecks FilledShapeChecks GradientChecks StrokeLogChecks StripGestureChecks TracingChecks CoverWearChecks PageStacksChecks CrispPageChecks LibraryCardChecks TemplateChecks TimedSectionsChecks NoErasingChecks PictureImportChecks WetPaintChecks LightOverDarkChecks StabilityChecks HoverNibChecks SelectionChecks LassoChecks ZoomFitChecks EdgeHoldChecks PageExportChecks StudioGuideChecks CodeSpecChecks LaterChecks LaterMediaChecks KnifeChecks SteadyHandChecks HoldSnapChecks MediaChecks ReprintChecks AreaChecks SurfaceChecks LiveChangeChecks LiveRevealChecks LiveToolsChecks LiveUndoChecks DrawMenusChecks LiveHostChecks LiveSendChecks LiveCryptChecks NotebookStripChecks FavouritesChecks SmudgeReachChecks LabColumnChecks SystemSettingsChecks ZenFanChecks VectorInkChecks VectorPdfChecks SharpChecks EraseSharpChecks EffectsLiveChecks ExportSizeChecks PngRenderChecks FridgeChecks PageWritingChecks WritingLinesChecks TranscriptChecks SecondInkChecks SecondInkFileChecks)
# Shared by checks, not run on their own.
helpers=(Timing WebParity NotebookFixture NotebookBench CoverTextures PresetPictures KnifePictures WetSmudgePictures SteadyHandPictures ReprintPictures AreaPictures SurfacePictures VectorInkPictures VectorInkBench VectorLargeBench EraseBench SharpBench SharpPictures SharpTools)
javac -d "$classes" $(for s in "${sources[@]}"; do echo "$main/$s.java"; done) \
  $(for c in "${checks[@]}" "${helpers[@]}"; do echo "$test/$c.java"; done)
for c in "${checks[@]}"; do java -cp "$classes" "io.github.mpdairy.monopaint.$c"; done
# Share on Wi-Fi's QR code read back by OpenCV's decoder, where it is installed.
if python3 -c "import cv2" 2>/dev/null; then
  qr_link="http://192.168.100.200:8080/live#k7qmw2xrh4tnpd9e"
  java -cp "$classes" io.github.mpdairy.monopaint.LiveHostChecks qr "$qr_link" "$classes/qr.pgm"
  qr_read=$(python3 -c "import cv2, sys; print(cv2.QRCodeDetector().detectAndDecode(cv2.imread(sys.argv[1], 0))[0])" "$classes/qr.pgm")
  [ "$qr_read" = "$qr_link" ] || { echo "FAIL: the QR code reads '$qr_read', not $qr_link"; exit 1; }
  echo "PASS: Share on Wi-Fi's QR code reads back as its link"
else
  echo "SKIP: the QR code's decode needs python3 with cv2"
fi
# The same strokes write the same bytes from one run to the next, on several cores (LaterMediaChecks.sameBytes, twice).
same_a=$(java -cp "$classes" io.github.mpdairy.monopaint.LaterMediaChecks fading-hash) same_b=$(java -cp "$classes" io.github.mpdairy.monopaint.LaterMediaChecks fading-hash)
[ "$same_a" = "$same_b" ] || { echo "FAIL: a page with Fading and Lemon Juice marks writes other bytes in another run ($same_a vs $same_b)"; exit 1; }
echo "PASS: a page with Fading and Lemon Juice marks writes the same bytes in two runs, on several cores"
# The web reader over the same test codes as CodeSpecChecks (docs/brush-code.json).
node docs/brush-lab/studio-src/code-spec-check.cjs
# The version before Later (ae4caeb8) reads this version's codes as docs/BRUSH-CODE.md says: a Later it holds in Drying in
# full, a new combination's six parts with the "Newer version" note.
later_ref=ae4caeb8
if git rev-parse -q --verify "$later_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$later_ref" "$main" | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderLaterCheck.java" 2>&1 | grep -v "^Note:" || true
  java -cp "$classes" io.github.mpdairy.monopaint.LaterChecks older > "$old_classes/codes.txt"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderLaterCheck "$old_classes/codes.txt"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the older brush code reader's check needs git and commit $later_ref"
fi
# The version before Second Ink (c7ebd5bb) reads a Second Ink On code as the plain brush, with the "Newer version" note.
second_ref=c7ebd5bb
if git rev-parse -q --verify "$second_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$second_ref" "$main" | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderLaterCheck.java" 2>&1 | grep -v "^Note:" || true
  java -cp "$classes" io.github.mpdairy.monopaint.LaterChecks second > "$old_classes/codes.txt"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderLaterCheck "$old_classes/codes.txt" "the version before Second Ink"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the check against the version before Second Ink needs git and commit $second_ref"
fi
# The version before Later's media (c360ed8e) reads their codes as Later Off, "Newer version"; opens a page with one of
# their marks as saved, the mark staying so; and wrote a page with Fading and Lemon Juice marks as this version does.
media_ref=c360ed8e
if git rev-parse -q --verify "$media_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$media_ref" "$main" | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderLaterCheck.java" "$test/OlderMediaCheck.java" 2>&1 | grep -v "^Note:" || true
  java -cp "$classes" io.github.mpdairy.monopaint.LaterChecks media > "$old_classes/codes.txt"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderLaterCheck "$old_classes/codes.txt" "the version before Later's media"
  java -cp "$classes" io.github.mpdairy.monopaint.LaterMediaChecks older-write "$old_classes/media.mpaint"
  page=$(java -cp "$classes" io.github.mpdairy.monopaint.LaterMediaChecks older-page -)
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderMediaCheck read "$old_classes/media.mpaint" "$page"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderMediaCheck fading "$old_classes/fading.mpaint"
  java -cp "$classes" io.github.mpdairy.monopaint.LaterMediaChecks fading-bytes "$old_classes/fading.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the check against the version before Later's media needs git and commit $media_ref"
fi
# The version before Second Ink (c7ebd5bb) skips a page's INKS section and opens the page as saved: the same tones.
ink_ref=c7ebd5bb
if git rev-parse -q --verify "$ink_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$ink_ref" "$main" | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderLaterCheck.java" "$test/OlderMediaCheck.java" 2>&1 | grep -v "^Note:" || true
  java -cp "$classes" io.github.mpdairy.monopaint.SecondInkFileChecks older-write "$old_classes/ink.mpaint"
  page=$(java -cp "$classes" io.github.mpdairy.monopaint.SecondInkFileChecks older-page "$old_classes/ink.mpaint")
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderMediaCheck read "$old_classes/ink.mpaint" "$page" "the version before Second Ink (c7ebd5bb)" "second ink"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the check against the version before Second Ink's page file needs git and commit $ink_ref"
fi
# The version before Later's print, crafts and ageing (9a57ae48) reads their codes as Later Off, "Newer version", and opens
# a page with one of their marks as saved, the mark staying so.
later2_ref=9a57ae48
if git rev-parse -q --verify "$later2_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$later2_ref" "$main" | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderLaterCheck.java" "$test/OlderMediaCheck.java" 2>&1 | grep -v "^Note:" || true
  java -cp "$classes" io.github.mpdairy.monopaint.LaterChecks later2 > "$old_classes/codes.txt"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderLaterCheck "$old_classes/codes.txt" "the version before Later's print, crafts and ageing"
  java -cp "$classes" io.github.mpdairy.monopaint.LaterMediaChecks older-write2 "$old_classes/media.mpaint"
  page=$(java -cp "$classes" io.github.mpdairy.monopaint.LaterMediaChecks older-page2 -)
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderMediaCheck read "$old_classes/media.mpaint" "$page" "the version before Later's print, crafts and ageing (9a57ae48)" "a Cross-Stitch mark"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the check against the version before Later's print, crafts and ageing needs git and commit $later2_ref"
fi
# The version before Vector Ink (04c30e6f) opens a page with its lines as saved: the LINE section passed by, the lines their pixels.
vector_ref=04c30e6f
if git rev-parse -q --verify "$vector_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$vector_ref" "$main" | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderVectorCheck.java" 2>&1 | grep -v "^Note:" || true
  java -cp "$classes" io.github.mpdairy.monopaint.VectorInkChecks older-write "$old_classes/vector.mpaint"
  page=$(java -cp "$classes" io.github.mpdairy.monopaint.VectorInkChecks older-page "$old_classes/vector.mpaint")
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderVectorCheck "$old_classes/vector.mpaint" "$page"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the check against the version before Vector Ink needs git and commit $vector_ref"
fi
# The version before Vector Ink followed the layer in use (1f0d16e8): this one opens its four-pair page with Vector Ink's
# own pair on top unchanged; it opens this one's page of Vector Ink beside a pen's marks on one layer as saved.
layers_ref=1f0d16e8
if git rev-parse -q --verify "$layers_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$layers_ref" "$main" | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderLayersCheck.java" 2>&1 | grep -v "^Note:" || true
  hash=$(java -cp "$old_classes" io.github.mpdairy.monopaint.OlderLayersCheck write "$old_classes/old.mpaint")
  java -cp "$classes" io.github.mpdairy.monopaint.VectorInkChecks old-layers "$old_classes/old.mpaint" "$hash"
  hash=$(java -cp "$classes" io.github.mpdairy.monopaint.VectorInkChecks mixed-write "$old_classes/mixed.mpaint")
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderLayersCheck read "$old_classes/mixed.mpaint" "$hash"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the check against the version before Vector Ink followed the layer needs git and commit $layers_ref"
fi
# The version before every edit was logged (56dad77f) opens a page whose log (version 2) holds eraser, smudge and Lasso
# entries as saved, without a replay (the notebook incident of 2026-11-02: the log is extra, the pixels are the page).
replay_ref=56dad77f
if git rev-parse -q --verify "$replay_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$replay_ref" "$main" | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderReplayCheck.java" 2>&1 | grep -v "^Note:" || true
  page=$(java -cp "$classes" io.github.mpdairy.monopaint.StrokeLogChecks older-write "$old_classes/replay.mpaint")
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderReplayCheck "$old_classes/replay.mpaint" "$page"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the check against the version before every edit was logged needs git and commit $replay_ref"
fi
# The version before shapes, pictures and merges were logged (1886cebf) reads a log with them as none: the page opens as
# saved, without a replay.
more_ref=1886cebf
if git rev-parse -q --verify "$more_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$more_ref" "$main" | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderReplayCheck.java" 2>&1 | grep -v "^Note:" || true
  page=$(java -cp "$classes" io.github.mpdairy.monopaint.StrokeLogChecks older-write-more "$old_classes/replay.mpaint")
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderReplayCheck "$old_classes/replay.mpaint" "$page" "the version before shapes, pictures and merges were logged ($more_ref)"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the check against the version before shapes, pictures and merges were logged needs git and commit $more_ref"
fi
# The version before Print Shading's Risograph, Photocopy and Soft Dither (9a57ae48) reads their codes as Print Shading
# None, the rest as written, "Newer version"; and the versions before Maze to Rings (47c1624d) and Pixels and Stitches (e8fdf657) theirs.
for screen_check in "9a57ae48 older riso, photocopy and soft dither" "47c1624d older-lined maze to rings" "e8fdf657 older-cells pixels and stitches"; do
  read -r screen_ref screen_list screen_what <<< "$screen_check"
  if git rev-parse -q --verify "$screen_ref^{commit}" >/dev/null 2>&1; then
    old_src=$(mktemp -d) old_classes=$(mktemp -d)
    git archive "$screen_ref" "$main" | tar -x -C "$old_src"
    javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderLaterCheck.java" 2>&1 | grep -v "^Note:" || true
    java -cp "$classes" io.github.mpdairy.monopaint.ScreenChecks "$screen_list" > "$old_classes/codes.txt"
    java -cp "$old_classes" io.github.mpdairy.monopaint.OlderLaterCheck "$old_classes/codes.txt" "the version before $screen_what"
    rm -rf "$old_src" "$old_classes"
  else
    echo "SKIP: the check against the version before $screen_what needs git and commit $screen_ref"
  fi
done
# The version before Reprint (9e447836) reads its codes as Smudge Off, the rest as written, "Newer version".
reprint_ref=9e447836
if git rev-parse -q --verify "$reprint_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$reprint_ref" "$main" | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderLaterCheck.java" 2>&1 | grep -v "^Note:" || true
  java -cp "$classes" io.github.mpdairy.monopaint.ReprintChecks older > "$old_classes/codes.txt"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderLaterCheck "$old_classes/codes.txt" "the version before Reprint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the check against the version before Reprint needs git and commit $reprint_ref"
fi
# A notebook from this version opens in the one before notebooks (580a326): its block of pictures is ignored.
old_ref=580a326
if git rev-parse -q --verify "$old_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  for f in GrayPalette DotPattern ToneDocument DocumentCodec DrawingBook BookCodec; do
    git show "$old_ref:app/src/main/java/io/github/mpdairy/monopaint/$f.java" > "$old_src/$f.java"
  done
  javac -d "$old_classes" "$old_src"/*.java "$test/OlderNotebookCheck.java"
  java -cp "$classes" io.github.mpdairy.monopaint.NotebookFileChecks write "$old_classes/notebook.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderNotebookCheck "$old_classes/notebook.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the older notebook reader's check needs git and commit $old_ref"
fi
# A notebook whose covers are drawn on opens in the version before covers (2a352ed, the Manta's), without them; one that
# version wrote opens here with plain covers and is written again byte for byte.
cover_ref=2a352ed
if git rev-parse -q --verify "$cover_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  for f in GrayPalette DotPattern ToneDocument DocumentCodec DrawingBook PackedPage BookCodec BookPictures InkWashLayers Magnets; do
    git show "$cover_ref:$main/$f.java" > "$old_src/$f.java"
  done
  javac -nowarn -d "$old_classes" "$old_src"/*.java "$test/OlderCoversCheck.java"
  java -cp "$classes" io.github.mpdairy.monopaint.CoverChecks write "$old_classes/covers.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderCoversCheck "$old_classes/covers.mpaint" "$old_classes/older.mpaint"
  java -cp "$classes" io.github.mpdairy.monopaint.CoverChecks older "$old_classes/older.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the older version's check for covers needs git and commit $cover_ref"
fi
# A notebook in a cover design opens in the covers version (6e3bae8), its DSGN record passed by; one that version saved
# again opens here with its covers, in Classic Black Marble.
design_ref=6e3bae8
if git rev-parse -q --verify "$design_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  for f in GrayPalette DotPattern ToneDocument DocumentCodec DrawingBook PackedPage BookCodec BookPictures InkWashLayers Magnets CoverMarble NotebookCovers BookOptions PagePicture LabText TypeStyle; do
    git show "$design_ref:$main/$f.java" > "$old_src/$f.java"
  done
  javac -nowarn -d "$old_classes" "$old_src"/*.java "$test/OlderDesignCheck.java"
  java -cp "$classes" io.github.mpdairy.monopaint.CoverDesignChecks write "$old_classes/designed.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderDesignCheck "$old_classes/designed.mpaint" "$old_classes/saved.mpaint"
  java -cp "$classes" io.github.mpdairy.monopaint.CoverDesignChecks older "$old_classes/saved.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the covers version's check for cover designs needs git and commit $design_ref"
fi
# A notebook with options (template, Guides in PDF, tools) opens in the version before them (233ba6b), their TMPL, GPDF and
# TOOL records passed by; one that version saved again opens here with the default options.
options_ref=233ba6b
if git rev-parse -q --verify "$options_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  for f in GrayPalette DotPattern ToneDocument DocumentCodec DrawingBook PackedPage BookCodec BookPictures InkWashLayers Magnets Mirrors CoverMarble CoverNoise CoverDesign CoverPattern CoverTexture Assets Cores NotebookCovers BookOptions PagePicture LabText TypeStyle DrawingFiles; do
    git show "$options_ref:$main/$f.java" > "$old_src/$f.java"
  done
  javac -nowarn -d "$old_classes" "$old_src"/*.java "$test/OlderOptionsCheck.java"
  java -cp "$classes" io.github.mpdairy.monopaint.NotebookOptionsChecks write "$old_classes/options.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderOptionsCheck "$old_classes/options.mpaint" "$old_classes/saved.mpaint"
  java -cp "$classes" io.github.mpdairy.monopaint.NotebookOptionsChecks older "$old_classes/saved.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the version before options' check needs git and commit $options_ref"
fi
# A notebook with a Second Ink opens in the version before it (c7ebd5bb), its SINK record passed by and the other options
# read as they were; one that version saved again opens here Blue.
ink_ref=c7ebd5bb
if git rev-parse -q --verify "$ink_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$ink_ref" app/src/main/java | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/SecondInkOlderCheck.java"
  java -cp "$classes" io.github.mpdairy.monopaint.SecondInkOptionChecks write "$old_classes/ink.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.SecondInkOlderCheck "$old_classes/ink.mpaint" "$old_classes/saved.mpaint"
  java -cp "$classes" io.github.mpdairy.monopaint.SecondInkOptionChecks older "$old_classes/saved.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the version before Second Ink's check needs git and commit $ink_ref"
fi
# A Tracing notebook (Paper, a ghost, steps) opens as drawn in the version before tracing paper (af06002), its PAPM, GHST and
# STEP records passed by; one that version saved again opens here Plain.
paper_ref=af06002
if git rev-parse -q --verify "$paper_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$paper_ref" app/src/main/java | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderOptionsCheck.java"
  java -cp "$classes" io.github.mpdairy.monopaint.TracingChecks write "$old_classes/tracing.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderOptionsCheck "$old_classes/tracing.mpaint" "$old_classes/saved.mpaint"
  java -cp "$classes" io.github.mpdairy.monopaint.TracingChecks older "$old_classes/saved.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the version before tracing paper's check needs git and commit $paper_ref"
fi
# A notebook of stacked layers (one page of two layers) opens in the version before stacks (2ddb2a4), its STAK record passed
# by: each layer a page of its own, as drawn; one that version saved again opens here as two pages.
stack_ref=2ddb2a4
if git rev-parse -q --verify "$stack_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$stack_ref" app/src/main/java | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderOptionsCheck.java"
  java -cp "$classes" io.github.mpdairy.monopaint.PageStacksChecks write "$old_classes/stacked.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderOptionsCheck "$old_classes/stacked.mpaint" "$old_classes/saved.mpaint"
  java -cp "$classes" io.github.mpdairy.monopaint.PageStacksChecks older "$old_classes/saved.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the version before stacks' check needs git and commit $stack_ref"
fi
# A page with text boxes opens in the version before them (9e447836), its TEXT section skipped, the ink made from one as drawn.
text_ref=9e447836
if git rev-parse -q --verify "$text_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$text_ref" "$main" | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/TextOlderReaderCheck.java" 2>&1 | grep -v "^Note:" || true
  where=$(java -cp "$classes" io.github.mpdairy.monopaint.TextBoxChecks write "$old_classes/text.mpaint")
  java -cp "$old_classes" io.github.mpdairy.monopaint.TextOlderReaderCheck "$old_classes/text.mpaint" $where
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the older reader's check for text boxes needs git and commit $text_ref"
fi
# A page with mirrors opens in the version before them (2327d14), its MIRR section skipped.
mirror_ref=2327d14
if git rev-parse -q --verify "$mirror_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  for f in GrayPalette DotPattern ToneDocument DocumentCodec Magnets; do
    git show "$mirror_ref:$main/$f.java" > "$old_src/$f.java"
  done
  javac -nowarn -d "$old_classes" "$old_src"/*.java "$test/MirrorOlderReaderCheck.java"
  where=$(java -cp "$classes" io.github.mpdairy.monopaint.MirrorChecks write "$old_classes/mirrors.mpaint")
  java -cp "$old_classes" io.github.mpdairy.monopaint.MirrorOlderReaderCheck "$old_classes/mirrors.mpaint" $where
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the older reader's check for mirrors needs git and commit $mirror_ref"
fi
# A page with kinds of mark opens in the version before them (e288fe9), its KIND section skipped.
kind_ref=e288fe9
if git rev-parse -q --verify "$kind_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$kind_ref" app/src/main/java | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderKindCheck.java"
  where=$(java -cp "$classes" io.github.mpdairy.monopaint.EraseKindChecks write "$old_classes/kinds.mpaint")
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderKindCheck "$old_classes/kinds.mpaint" $where
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the older reader's check for kinds needs git and commit $kind_ref"
fi
# A notebook whose cover's design is rubbed off opens in the version before (ca133f7), its WORN section skipped, the design whole.
wear_ref=ca133f7
if git rev-parse -q --verify "$wear_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$wear_ref" app/src/main/java | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderWearCheck.java"
  java -cp "$classes" io.github.mpdairy.monopaint.CoverWearChecks write "$old_classes/worn.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderWearCheck "$old_classes/worn.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the older reader's check for cover wear needs git and commit $wear_ref"
fi
# A page with started Timed Sections opens as drawn in the version before them (a1328a8), its SECT section skipped.
pace_ref=a1328a8
if git rev-parse -q --verify "$pace_ref^{commit}" >/dev/null 2>&1; then
  old_src=$(mktemp -d) old_classes=$(mktemp -d)
  git archive "$pace_ref" app/src/main/java | tar -x -C "$old_src"
  javac -nowarn -d "$old_classes" -sourcepath "$old_src/app/src/main/java" "$test/OlderSectionsCheck.java"
  java -cp "$classes" io.github.mpdairy.monopaint.TimedSectionsChecks write "$old_classes/sections.mpaint"
  java -cp "$old_classes" io.github.mpdairy.monopaint.OlderSectionsCheck "$old_classes/sections.mpaint"
  rm -rf "$old_src" "$old_classes"
else
  echo "SKIP: the version before timed sections' check needs git and commit $pace_ref"
fi
# The native renderer (app/src/main/cpp/compose.h) against the Java one, on white paper and on a shaded page.
javac -d "$classes" -cp "$classes" app/src/test/java/io/github/mpdairy/monopaint/ComposeFixture.java
cc -std=c99 -O2 -Wall -Wextra -Werror -o "$classes/compose_check" app/src/test/cpp/compose_check.c
java -cp "$classes" io.github.mpdairy.monopaint.ComposeFixture "$classes/compose.bin"
"$classes/compose_check" "$classes/compose.bin"
java -cp "$classes" io.github.mpdairy.monopaint.ComposeFixture "$classes/compose.bin" 97
"$classes/compose_check" "$classes/compose.bin"
# And on a notebook cover, its marble under the layers.
java -cp "$classes" io.github.mpdairy.monopaint.ComposeFixture "$classes/compose.bin" cover
"$classes/compose_check" "$classes/compose.bin"
# The end of a stroke shown through the panel's fast update (app/src/main/cpp/panel_end.h) against a fake driver.
cc -std=c99 -O2 -Wall -Wextra -Werror -o "$classes/panel_end_check" app/src/test/cpp/panel_end_check.c
"$classes/panel_end_check"
