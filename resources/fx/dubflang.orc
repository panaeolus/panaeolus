;; These tables were taken from a supercollider sandbox, kudos to whom who made them
;; I discovered that these sound very good as an effect! (Hlöðver, May 2019)

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

event_i "i", 2, 0, 0

gkrate init 1/2
gkfreq init 50

instr 4
  ain1, ain2 ins
  aflang1 init 0
  aflang2 init 0
  atraverser = abs:a(oscil:a(1, gkrate/2, -1))
  ioffset = 1000
  aphasor = abs:a(oscil:a(1, gkfreq/2, -1))

  alfo tablexkt atraverser, ioffset + ((giTabCnt - 1)  * abs:k(downsamp(aphasor))), 8, 1024, 0, 0, 0

  aflang1, aflang2 vdelayxws ain1, ain2, abs:a(alfo), 0.5, 256
  outs aflang1, aflang2
endin

instr 5
  gkrate = p4
  gkfreq = p5
endin

event_i "i", 4, 0, -1
