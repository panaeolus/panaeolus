;; Loaded tables keep their number and data for the lifetime of the engine.
;; Ten million values bound retained sample memory; exports have a separate
;; 64 MiB text limit because Base64 expands the embedded double-precision WAVs.
panaeolusSamplePaths@global:S[] init 256
panaeolusSampleTables@global:i[] init 256
panaeolusSampleFrames@global:i[] init 256
panaeolusSampleChannels@global:i[] init 256
panaeolusSampleRates@global:i[] init 256
panaeolusSampleGains@global:i[] init 256
panaeolusSampleLoudness@global:i[] init 256
panaeolusSampleNames@global:S[] init 256
panaeolusSampleCount@global:i init 0
panaeolusSampleValues@global:i init 0
panaeolusTakeSamples@global:i init 0
panaeolusSampleExportSerial@global:i init 0
panaeolusSampleExportNamesReady@global:i init 0
panaeolusSampleExportStack@global:S[] init 16
panaeolusSampleExportOccupied@global:i[] init 16

opcode PanaeolusSampleInfo(index:i):MalValue
  result:MalValue init MalMkValue($MAL_HASH_MAP_TYPE)
  result init MalMapAssoc(result, MalMkKeyword("table"), MalMkNumber(panaeolusSampleTables[index]))
  result init MalMapAssoc(result, MalMkKeyword("frames"), MalMkNumber(panaeolusSampleFrames[index]))
  result init MalMapAssoc(result, MalMkKeyword("channels"), MalMkNumber(panaeolusSampleChannels[index]))
  result init MalMapAssoc(result, MalMkKeyword("sample-rate"), MalMkNumber(panaeolusSampleRates[index]))
  result init MalMapAssoc(result, MalMkKeyword("engine-rate"), MalMkNumber(sr))
  result init MalMapAssoc(result, MalMkKeyword("duration"), \
    MalMkNumber(panaeolusSampleFrames[index] / panaeolusSampleRates[index]))
  result init MalMapAssoc(result, MalMkKeyword("level-gain"), MalMkNumber(panaeolusSampleGains[index]))
  result init MalMapAssoc(result, MalMkKeyword("loudness"), MalMkNumber(panaeolusSampleLoudness[index]))
  xout result
endop

opcode PanaeolusSampleLoad(token:i):i
  request:MalValue init PanaeolusLoopRequest(token)
  path:MalValue init MalMapGet(request, MalMkKeyword("path"))
  result:MalValue init MalMkError("sample: expected a nonempty file path")
  existing:i = -1
  if (path.type == $MAL_STRING_TYPE && strlen(path.string) > 0) ithen
    if (panaeolusSampleCount > 0) ithen
      for index in [0 ... panaeolusSampleCount - 1] do
        if (strcmp(panaeolusSamplePaths[index], path.string) == 0) ithen
          existing = index
          break
        endif
      od
    endif
    if (existing >= 0) ithen
      result init PanaeolusSampleInfo(existing)
    elseif (panaeolusSampleCount >= lenarray(panaeolusSampleTables)) ithen
      result init MalMkError("sample: the engine already holds 256 sample files")
    elseif (filevalid(path.string) == 0) ithen
      result init MalMkError("sample: file does not exist")
    else
      ;; File inspection can abort this init pass. Leave a useful reply first,
      ;; and reject unknown formats instead of interpreting arbitrary bytes as
      ;; raw audio. Nothing enters the sample registry until loading succeeds.
      PanaeolusLoopReply(token, MalMkError("sample: could not read audio file"))
      channels:i = filenchnls(path.string, 0)
      rate:i = filesr(path.string, 0)
      frames:i = round(filelen(path.string, 0) * rate)
      values:i = frames * channels
      if (channels != 1 && channels != 2) ithen
        result init MalMkError("sample: only mono and stereo files are supported")
      elseif (rate <= 0 || frames < 6) ithen
        result init MalMkError("sample: a file needs at least six frames and a valid sample rate")
      elseif (rate > 384000) ithen
        ;; Bound the normalization filter's 400 ms padding for short files.
        result init MalMkError("sample: sample rates above 384000 Hz are not supported")
      elseif (values > 10000000 - panaeolusSampleValues) ithen
        result init MalMkError("sample: loading this file would exceed ten million stored sample values")
      else
        number:i ftgen 0, 0, 0, -1, path.string, 0, 0, 0
        frames = nsamp(number)
        channels = ftchnls(number)
        rate = ftsr(number)
        values = frames * channels
        valid:i = 1
        if (frames < 6 || (channels != 1 && channels != 2) || rate <= 0 || \
            values > ftlen(number) || values > 10000000 - panaeolusSampleValues) ithen
          valid = 0
          result init MalMkError("sample: loaded audio has invalid metadata or exceeds the sample limit")
        else
          for position in [0 ... values - 1] do
            value:i = table:i(position, number)
            if (value - value != 0) ithen
              valid = 0
              result init MalMkError("sample: the file contains non-finite audio values")
              break
            elseif (abs(value) > 1e100) ithen
              ;; Finite extremes can still overflow flooper2's interpolation.
              ;; Leave ample headroom for differences, fades, and gain changes.
              valid = 0
              result init MalMkError("sample: unsafe audio magnitude (decoded values must not exceed 1e100)")
              break
            endif
          od
        endif
        if (valid == 0) ithen
          ftfree number, 0
        else
          levelGain:i, loudness:i = PanaeolusSampleLevel(number, frames, channels, rate)
          index:i = panaeolusSampleCount
          panaeolusSampleGains[index] = levelGain
          panaeolusSampleLoudness[index] = loudness
          panaeolusSamplePaths[index] init path.string
          panaeolusSampleTables[index] = number
          panaeolusSampleFrames[index] = frames
          panaeolusSampleChannels[index] = channels
          panaeolusSampleRates[index] = rate
          panaeolusSampleValues += values
          panaeolusSampleCount += 1
          result init PanaeolusSampleInfo(index)
        endif
      endif
    endif
  endif
  ;; Transport succeeded even when the reply holds a Lisp error. Returning -1
  ;; here would replace that useful message with the bridge's generic error.
  PanaeolusLoopReply(token, result)
  status:i = 0
  xout status
endop

opcode PanaeolusSampleExportFailure(message:S):void
  chnset -1, "__panaeolus_sample_export_status"
  chnset message, "__panaeolus_error"
  panaeolusSampleExportNamesReady = 0
endop

;; Merge equal-sized groups as chunks arrive. Each level holds at most one
;; group, so joining a large sample never repeatedly copies its whole prefix.
opcode PanaeolusSampleAppend(chunk:S):void
  carry:S init chunk
  level:i = 0
  while (panaeolusSampleExportOccupied[level] == 1) do
    carry strcat panaeolusSampleExportStack[level], carry
    panaeolusSampleExportOccupied[level] = 0
    level += 1
  od
  panaeolusSampleExportStack[level] init carry
  panaeolusSampleExportOccupied[level] = 1
endop

opcode PanaeolusSampleBase64(bytes:i[], count:i):S
  alphabet:S init "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
  result:S init ""
  position:i = 0
  while (position < count) do
    first:i = bytes[position]
    second:i = 0
    third:i = 0
    if (position + 1 < count) ithen
      second = bytes[position + 1]
    endif
    if (position + 2 < count) ithen
      third = bytes[position + 2]
    endif
    a:i = strchar:i(alphabet, int(first / 4))
    b:i = strchar:i(alphabet, (first % 4) * 16 + int(second / 16))
    c:i = 61
    d:i = 61
    if (position + 1 < count) ithen
      c = strchar:i(alphabet, (second % 16) * 4 + int(third / 64))
    endif
    if (position + 2 < count) ithen
      d = strchar:i(alphabet, third % 64)
    endif
    result strcat result, sprintf("%c%c%c%c", a, b, c, d)
    position += 3
  od
  xout result
endop

opcode PanaeolusSampleWord(bytes:i[], position:i, word:i, size:i):void
  for byte in [0 ... size - 1] do
    bytes[position + byte] = int(word / (256 ^ byte)) % 256
  od
endop

opcode PanaeolusSampleWave(index:i):void
  ;; 12 KiB becomes 16 KiB of Base64. This bounds the small string appends
  ;; inside each chunk, while the merge stack handles the complete file.
  bytes:i[] init 12288
  count:i = panaeolusSampleFrames[index] * panaeolusSampleChannels[index]
  channels:i = panaeolusSampleChannels[index]
  rate:i = panaeolusSampleRates[index]
  number:i = panaeolusSampleTables[index]
  PanaeolusSampleWord(bytes, 0, 1179011410, 4) ; RIFF
  PanaeolusSampleWord(bytes, 4, count * 8 + 36, 4)
  PanaeolusSampleWord(bytes, 8, 1163280727, 4) ; WAVE
  PanaeolusSampleWord(bytes, 12, 544501094, 4) ; fmt
  PanaeolusSampleWord(bytes, 16, 16, 4)
  PanaeolusSampleWord(bytes, 20, 3, 2) ; IEEE_FLOAT
  PanaeolusSampleWord(bytes, 22, channels, 2)
  PanaeolusSampleWord(bytes, 24, rate, 4)
  PanaeolusSampleWord(bytes, 28, rate * channels * 8, 4)
  PanaeolusSampleWord(bytes, 32, channels * 8, 2)
  PanaeolusSampleWord(bytes, 34, 64, 2)
  PanaeolusSampleWord(bytes, 36, 1635017060, 4) ; data
  PanaeolusSampleWord(bytes, 40, count * 8, 4)
  position:i = 44
  for sample in [0 ... count - 1] do
    ;; GEN01 scales WAV samples by 0dbfs when reading. Undo that scale for
    ;; the embedded file; the exported orchestra restores the same 0dbfs.
    value:i = table:i(sample, number) / 0dbfs
    magnitude:i = abs(value)
    sign:i = (value < 0 ? 2147483648 : 0)
    ;; atan2 keeps the sign of zero, including an underflowed negative sample.
    if (value == 0 && taninv2(value, -1) < 0) ithen
      sign = 2147483648
    endif
    high:i = sign
    low:i = 0
    if (magnitude > 0) ithen
      if (magnitude < 2.2250738585072014e-308) ithen
        mantissa:i = (magnitude / 2.2250738585072014e-308) * 4503599627370496
        high += int(mantissa / 4294967296)
        low = mantissa % 4294967296
      else
        exponent:i = min(1023, floor(log(magnitude) / log(2)))
        scale:i = 2 ^ exponent
        ;; log can round to the adjacent exponent at a power of two.
        if (magnitude < scale) ithen
          exponent -= 1
          scale /= 2
        elseif (magnitude / scale >= 2) ithen
          exponent += 1
          scale *= 2
        endif
        mantissa = (magnitude / scale - 1) * 4503599627370496
        high += (exponent + 1023) * 1048576 + int(mantissa / 4294967296)
        low = mantissa % 4294967296
      endif
    endif
    for byte in [0 ... 7] do
      word:i = (byte < 4 ? low : high)
      shift:i = byte % 4
      bytes[position] = int(word / (256 ^ shift)) % 256
      position += 1
      if (position == lenarray(bytes)) ithen
        PanaeolusSampleAppend(PanaeolusSampleBase64(bytes, position))
        position = 0
      endif
    od
  od
  if (position > 0) ithen
    PanaeolusSampleAppend(PanaeolusSampleBase64(bytes, position))
  endif
endop

opcode PanaeolusSampleFiles():S
  result:S init ""
  chnset 0, "__panaeolus_sample_export_status"
  panaeolusSampleExportNamesReady = 0
  total:i = 0
  if (panaeolusTakeSamples > 0) ithen
    for index in [0 ... panaeolusTakeSamples - 1] do
      values:i = panaeolusSampleFrames[index] * panaeolusSampleChannels[index]
      total += ceil((44 + values * 8) / 3) * 4 + 256
    od
  endif
  if (total > 67108864) ithen
    PanaeolusSampleExportFailure("export: embedded sample files exceed the 64 MiB text limit")
  else
    for level in [0 ... lenarray(panaeolusSampleExportOccupied) - 1] do
      panaeolusSampleExportOccupied[level] = 0
    od
    panaeolusSampleExportSerial += 1
    stamp:i = round(rtclock() * 1000000)
    if (panaeolusTakeSamples > 0) ithen
      for index in [0 ... panaeolusTakeSamples - 1] do
        name:S init sprintf("__panaeolus_%.0f_%d_%d.wav", stamp, panaeolusSampleExportSerial, index)
        while (filevalid(name) == 1) do
          panaeolusSampleExportSerial += 1
          name init sprintf("__panaeolus_%.0f_%d_%d.wav", stamp, panaeolusSampleExportSerial, index)
        od
        panaeolusSampleNames[index] init name
        PanaeolusSampleAppend(sprintf("<CsFileB filename=\"%s\">\n", name))
        PanaeolusSampleWave(index)
        PanaeolusSampleAppend("\n</CsFileB>\n")
      od
    endif
    for step in [0 ... lenarray(panaeolusSampleExportOccupied) - 1] do
      level:i = lenarray(panaeolusSampleExportOccupied) - 1 - step
      if (panaeolusSampleExportOccupied[level] == 1) ithen
        result strcat result, panaeolusSampleExportStack[level]
      endif
    od
    panaeolusSampleExportNamesReady = 1
  endif
  xout result
endop

opcode PanaeolusSampleOrchestra():S
  result:S init ""
  if (panaeolusSampleExportNamesReady == 0) ithen
    PanaeolusSampleExportFailure("export: sample files must be assembled before their orchestra")
  elseif (panaeolusTakeSamples > 0) ithen
    for index in [0 ... panaeolusTakeSamples - 1] do
      result strcat result, sprintf("panaeolusSample%d@global:i ftgen %d, 0, 0, -1, \"%s\", 0, 0, 0\n", \
        index, panaeolusSampleTables[index], panaeolusSampleNames[index])
    od
  endif
  xout result
endop
