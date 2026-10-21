// beta36 has two WASI file bugs: stat reports the last write buffer's size,
// and reads past EOF report requested bytes instead of copied bytes. The first
// breaks embedded samples; the second adds stale text to a loaded MAL bundle.
// Patch the build copy and its two workers, leaving the package/cache untouched.
// Remove these patches after upgrading to a release with the source fixes.
export function csoundModule(source: string, version: string): string {
  if (version !== "7.0.0-beta36") {
    throw new Error("Review the Csound file patches before changing versions.");
  }
  const broken = /\.reduce\(function\(e,([fg])\)\{return e\+\(\1==null\?void 0:\1\.byteLength\)\?\1==null\?void 0:\1\.byteLength:0\},0\)/g;
  let count = 0;
  let result = source.replace(broken, (_match, buffer) => {
    count++;
    return `.reduce(function(e,${buffer}){return e+(${buffer}==null?0:${buffer}.byteLength)},0)`;
  });
  if (count !== 3) throw new Error(`Expected three Csound file-size reducers, found ${count}.`);
  // Replace fd_read as a unit: its byte loop also skips chunks when a seek
  // lands past the first buffer. Copy bounded spans and count only those bytes.
  const reader = /\.prototype\.(\w+)=function\(a,b,c,d\)\{var e=this\.getMemory\(\);a=this\.fd\[a\];if\(!a\|\|!Array\.isArray\(a\.buffers\)\)[\s\S]*?return 0\};(?=[A-Za-z_$]\("fd_read",)/g;
  count = 0;
  result = result.replace(reader, (_match, name) => {
    count++;
    return `.prototype.${name}=function(fd,iovs,iovsLength,nread){` +
      `var memory=this.getMemory(),entry=this.fd[fd];` +
      `if(!entry||!Array.isArray(entry.buffers)){memory.setUint32(nread,0,true);return 8}` +
      `var buffers=entry.buffers,position=Number(entry.seekPos),chunk=0,offset=position,copied=0;` +
      `if(!Number.isSafeInteger(position)||position<0){memory.setUint32(nread,0,true);return 28}` +
      `while(chunk<buffers.length&&offset>=buffers[chunk].byteLength){offset-=buffers[chunk].byteLength;chunk++}` +
      `for(var i=0;i<iovsLength&&chunk<buffers.length;i++){` +
      `var pointer=iovs+i*8,target=memory.getUint32(pointer,true),remaining=memory.getUint32(pointer+4,true);` +
      `while(remaining>0&&chunk<buffers.length){` +
      `var data=buffers[chunk],length=Math.min(remaining,data.byteLength-offset);` +
      `new Uint8Array(memory.buffer,target,length).set(data.subarray(offset,offset+length));` +
      `target+=length;remaining-=length;copied+=length;offset+=length;` +
      `if(offset===data.byteLength){chunk++;offset=0}}}` +
      `entry.seekPos=BigInt(position+copied);memory.setUint32(nread,copied,true);return 0};`;
  });
  if (count !== 3) throw new Error(`Expected three Csound file readers, found ${count}.`);
  return result;
}
