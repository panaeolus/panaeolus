;; These tables were taken from a supercollider sandbox, kudos to whom who made them
;; I discovered that these sound very good as an effect! (Hlöðver, May 2019)


;; 'midpoint mischief': { |x, z| 0.5 * cos (x * 0.5pi) * (x.sin * pi + ((1 - z) * sin(z * pow(x * x, z) * 32pi))) }

opcode mischiefTable,0,iiio
  isize,istartnum,iamount,inum xin
  if (inum < iamount) then
    gitbl ftgen inum+istartnum, 0, isize, -2, 0
    iz = (1 + inum) / (0.5 * inum)
    indx = 0
    while (indx < isize) do
      ix = (1 + inum) / (2 * iamount)
    ires1 = (1 - iz) * sin:i(iz * pow:i(ix^2, iz) * (iamount / 16))
    ires2 = 0.5 * sin:i(ix) * cos:i(0.5 * ix * $M_PI)
      ires3 = ires1 + ires2
      indx += 1
      tablew ires3, indx, gitbl, 0, 0, 1
    od
    mischiefTable isize, istartnum, iamount, inum + 1
  endif
endop


;; 'taffy': { |x, z| sin (x * 2pi) * cos (x * pi) * cos (z * pi * pi * (abs(pow(x * 2, 3) - 1))) },

opcode taffyTable,0,iiio
  isize,istartnum,iamount,inum xin
  if (inum < iamount) then
    gitbl ftgen inum+istartnum, 0, isize, -2, 0
    iz = inum / iamount
    indx = 0
    while (indx < isize) do
      ix = indx / isize
    tablew sin:i(ix * (2*$M_PI)) * cos:i(ix * $M_PI) * \
                    cos:i(iz * $M_PI * $M_PI * (abs:i(pow:i(ix * 2, 3) - 1))), \
                    indx, gitbl, 0, 0, 1
      indx += 1
    od
    taffyTable isize, istartnum, iamount, inum + 1
  endif
endop

giTabSize = 1024
giTabCnt = 512

instr 2
  istartnum = 1000
  taffyTable giTabSize, istartnum, giTabCnt
endin


instr 3
  istartnum = 2000
  mischiefTable giTabSize, istartnum, giTabCnt
endin

event_i "i", 2, 0, 0
event_i "i", 3, 0, 0

gkrate init 0.1
gkdelay init 0.1
gkfreq init 200


instr 4
  setksmps 1
  ain1, ain2 ins
  aflang1 init 0
  aflang2 init 0
  kdelay portk gkdelay, 0.1
  atraverser = poscil:k(1, gkrate/2)
  ioffset = 1000
  ;; if (gkmode == 0) then
  ;;   ioffset = 1000
  ;; else
  ;;   ioffset = 2000
  ;; endif
  aphasor phasor gkfreq
  alfo tablexkt aphasor, ioffset + ((giTabCnt - 1) * abs:k(downsamp(atraverser))), 4, 256, 0, 0, 0

  aflang1, aflang2 vdelayxws ain1, ain2, kdelay * abs:a(alfo), 2, 16
  aflang1 dcblock2 aflang1
  aflang2 dcblock2 aflang2
  outs aflang1, aflang2
endin

instr 5
  gkrate = p4
  gkdelay = p5
  gkfreq = p6
endin

event_i "i", 4, 0, -1
