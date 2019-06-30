giCos ftgen 0,0,4096,11,1
giSine ftgen 0, 0, 65537, 10, 1

instr 1
  ishift = .00666667               ;shift it 8/1200.
  iphase = p6
  ifreq = p4
  kadsr linseg 0, p3/3, 1.0, p3/3, 1.0, p3/3, 0 ;ADSR envelope
  kmodi linseg 0, p3/3, 5, p3/3, 3, p3/3, 0 ;ADSR envelope for I
  ip6 random 0.1, 0.4
  ip7 random 1, 3
  kmodr linseg ip6, p3, ip7              ;r moves from p6->p7 in p3 sec.
  a1 = kmodi*(kmodr-1/kmodr)/2
  a1ndx = abs(a1*2/20)            ;a1*2 is normalized from 0-1.
  a2 = kmodi*(kmodr+1/kmodr)/2
  a3 tablei a1ndx, giSine, 1  ;lookup tbl in f3, normal index
  ao1 poscil a1, iphase, giCos  ;cosine
  a4 = exp(-0.5*a3+ao1)
  ao2 poscil a2*iphase, iphase, giCos        ;cosine
  aoutl poscil kadsr*a4, ao2+cpsmidinn(ifreq+ishift)  ;fnl outleft
  aoutr poscil kadsr*a4, ao2+cpsmidinn(ifreq-ishift)  ;fnl outright
  iamp = ampdbfs(p5)
  aL = aoutl*iamp*0.01
  aR = aoutr*iamp*0.01
  ;; adeclick linseg 0, 0.02, 1, p3 - 0.05, 1, 0.02, 0, 0.01, 0
  ;; aL *= adeclick*0.2
  ;; aR *= adeclick*0.2
  outs aL, aR

endin
