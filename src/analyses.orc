;; Generated analysis modules install immutable files before playback. Keep the
;; text outside MAL values, where repeated copies retain large string buffers.
panaeolusAnalysisPaths@global:S[] init 64
panaeolusAnalysisFiles@global:S[] init 64
panaeolusAnalysisCount@global:i init 0
panaeolusAnalysisBytes@global:i init 0
panaeolusTakeAnalyses@global:i init 0
panaeolusAnalysisExportSerial@global:i init 0

opcode PanaeolusInstallAnalysis(path:S, data:S, embedded:S):i
  result:i = -1
  index:i = 0
  while (index < panaeolusAnalysisCount) do
    if (strcmp(path, panaeolusAnalysisPaths[index]) == 0) ithen
      if (strcmp(embedded, panaeolusAnalysisFiles[index]) == 0) ithen
        result = index
      endif
      igoto DONE
    endif
    index += 1
  od
  if (panaeolusAnalysisCount >= 64 || strlen(data) > 262144 || \
      panaeolusAnalysisBytes + strlen(embedded) > 8388608) ithen
    igoto DONE
  endif
  ;; An explicit handle truncates an old file and closes it before adsyn reads.
  handle:i fiopen path, 0
  offset:i = 0
  while (offset < strlen(data)) do
    chunk:S init strsub(data, offset, offset + 1024)
    fprints handle, "%s", chunk
    offset += 1024
  od
  ficlose handle
  result = panaeolusAnalysisCount
  panaeolusAnalysisPaths[result] init path
  panaeolusAnalysisFiles[result] init embedded
  panaeolusAnalysisCount += 1
  panaeolusAnalysisBytes += strlen(embedded)
DONE:
  xout result
endop

opcode PanaeolusAnalysisExport():S
  files:S init ""
  orchestra:S init ""
  stamp:i = round(rtclock() * 1000000)
  panaeolusAnalysisExportSerial += 1
  index:i = 0
  while (index < panaeolusTakeAnalyses) do
    ;; CsFileB refuses existing filenames, even when the bytes match. Export
    ;; fresh names and map the stable graph identity to each embedded asset.
    name:S init sprintf("__panaeolus_analysis_%.0f_%d_%d.het", stamp, panaeolusAnalysisExportSerial, index)
    while (filevalid(name) == 1) do
      panaeolusAnalysisExportSerial += 1
      name init sprintf("__panaeolus_analysis_%.0f_%d_%d.het", stamp, panaeolusAnalysisExportSerial, index)
    od
    files strcat files, sprintf("<CsFileB filename=\"%s\">\n%s\n</CsFileB>\n", name, panaeolusAnalysisFiles[index])
    orchestra strcat orchestra, sprintf("chnset \"%s\", \"%s\"\n", name, panaeolusAnalysisPaths[index])
    index += 1
  od
  chnset orchestra, "__panaeolus_analysis_orchestra"
  xout files
endop
