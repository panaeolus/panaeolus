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
