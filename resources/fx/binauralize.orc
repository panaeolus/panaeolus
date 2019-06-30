gkportTime init 1

opcode binauralize, aa, akk
  ain,kcent,kdiff xin
  ifftsz = 1024
  ; determine pitches
  kp1 = kcent + (kdiff/2)
  kp2 = kcent - (kdiff/2)
  krat1 = kp1 / kcent
  krat2	= kp2 / kcent
  krat1 portk krat1, gkportTime
  krat2 portk krat2, gkportTime
  ; take it apart
  fsig pvsanal	ain, ifftsz, ifftsz/4, ifftsz, 1
  ; create derived streams
  fbinL	pvscale	fsig, krat1, 1
  fbinR	pvscale	fsig, krat2, 1
  ; put it back together
  abinL	pvsynth	fbinL
  abinR	pvsynth	fbinR
  ; send it out
  xout abinL, abinR
endop

gkcent init 0.6
gkdiff init 0.8

instr 1
  ain1, ain2 ins
  a1, a2 binauralize (ain1+ain2)/1.3, gkcent, gkdiff
  afader init 0
  if (p3 < 0) then
    afader expseg 0.001, 0.1, 1, 99999999, 1
  else
    afader expseg 1, p3, 0.001
  endif
  outs a1*afader, a2*afader
endin
schedule(1, 0, -1)

instr 2
  gkportTime = p3
  gkcent = p4
  gkdiff = p5
endin
