opcode exciter, aa, aakkkkk
  asigL,asigR,kfreq,kport,kceil,kharmonics,kblend xin
  kfreq = max:k(kfreq,20)
  kceilabs = min:k(sr/2, abs(kceil)*kfreq)
  kamplevel = max:k(0.1, (1 - (kharmonics/50)))
  if kceil < 0 then
    kceil rspline kceilabs, kfreq/2, kport/2, kport
    asig exciter (asigL+asigR)*0.9, kfreq, kceil, kharmonics, kblend
  else
    ;; adeclick linseg 0, 0.02, 1, p3 - 0.05, 1, 0.02, 0, 0.01, 0
    kceil rspline kfreq/2, kceilabs, kport/2, kport
    asig exciter (asigL+asigR)*kamplevel, kfreq, kceil, kharmonics, kblend
    ;; asig *= adeclick
  endif

  xout asig, asig
endop

;; ceil can be negative

gkport init 1
gkfreq init 100
gkceil init 1
gkharmonics init 5
gkblend init 1

instr 1
  kfreq portk gkfreq, gkport
  kceil portk gkceil, gkport
  kharmonics portk gkharmonics, gkport
  kblend portk gkblend, gkport

  aInL, aInR ins

  afader init 0
  if (p3 < 0) then
    printk 1, 1
    afader expseg 0.001, 0.1, 1, 99999999, 1
  else
    printk -1, 1
    afader expseg 1, p3, 0.001
  endif

  aL, aR exciter aInL, aInR, kfreq, gkport, kceil, kharmonics, kblend

  outs aL*afader, aR*afader
endin

instr 2
  gkport = p3
  gkfreq = p4
  gkceil = p5
  gkharmonics = p6
  gkblend = p7
endin

alwayson(1, 0, -1)
