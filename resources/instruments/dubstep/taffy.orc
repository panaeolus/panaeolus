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

instr 2
  isize = 1024
  istartnum = 1000
  iamount = 513
  taffyTable isize, istartnum, iamount
endin

event_i("i", 2, 0, 0)

instr 3
  ioffset = 1000
  iamount = 512
  idepth = p7
  iwobbles = p6
  ilpf = p8
  ires = p9
  ktable poscil idepth * iamount, (iwobbles/p3) * 0.5 , -1
  ktable abs ktable
  afreq  phasor cpsmidinn(p4)
  asig tablexkt afreq, ioffset + int:k(ktable), 0, 32, 1, 0, 0
  asub poscil ampdbfs(p5), cpsmidinn(p4)/2, -1
  asum sum asig, asub
  afiltered moogvcf2 asum, ilpf, ires
  aenv madsr 0.1, p3-0.2, 1, 0.1
  aout = aenv * afiltered * ampdbfs(p5)
  outs aout, aout
endin
