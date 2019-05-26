# npx electron-packager resources/main.js panaeolus
with import <nixpkgs> {};

stdenv.mkDerivation {
  name = "electron-libpatcher";
  buildInputs = with pkgs; [ glib nss nspr gdk_pixbuf gtk3 pango atk cairo dbus
                             xorg.libXext xorg.libXcomposite xorg.libXrender
                             xorg.libXcursor xorg.libXdamage xorg.libXfixes
                             xorg.libXi xorg.libXtst expat utillinux
                             xorg.libXrandr xorg.libXScrnSaver alsaLib at-spi2-atk
                             at-spi2-core cups
                             electron_5
];

  LD_PRELOAD = "${xorg.libXScrnSaver}/lib/libXss.so.1";
  shellHook = ''
  # cmd: sh -c patchElectron

  # echo ${cups.lib}/lib/
  patchElectron () {
    cp -f ${glib.out}/lib/libgobject-2.0.so.0.6000.0 ./libgobject-2.0.so.0
    cp -f ${glib.out}/lib/libglib-2.0.so.0.6000.0 ./libglib-2.0.so.0
    cp -f ${glib.out}/lib/libgio-2.0.so.0.6000.0 ./libgio-2.0.so.0
    cp -f ${nss}/lib/libnss3.so ./libnss3.so
    cp -f ${nss}/lib/libnssutil3.so ./libnssutil3.so
    cp -f ${nss}/lib/libsmime3.so ./libsmime3.so
    cp -f ${nspr}/lib/libnspr4.so ./libnspr4.so
    cp -f ${gdk_pixbuf}/lib/libgdk_pixbuf-2.0.so.0.3800.1 ./libgdk_pixbuf-2.0.so.0
    cp -f ${gtk3}/lib/libgtk-3.so.0.2404.4 ./libgtk-3.so.0
    cp -f ${gtk3}/lib/libgdk-3.so.0.2404.4 ./libgdk-3.so.0
    cp -f ${pango.out}/lib/libpangocairo-1.0.so.0.4300.0 ./libpangocairo-1.0.so.0
    cp -f ${pango.out}/lib/libpango-1.0.so.0.4300.0 ./libpango-1.0.so.0
    cp -f ${atk}/lib/libatk-1.0.so.0.23209.1 ./libatk-1.0.so.0
    cp -f ${cairo}/lib/libcairo.so.2.11600.0 ./libcairo.so.2
    cp -f ${dbus.lib}/lib/libdbus-1.so.3.19.9 ./libdbus-1.so.3
    cp -f ${xorg.libXext}/lib/libXext.so.6.4.0 ./libXext.so.6
    cp -f ${xorg.libXcomposite}/lib/libXcomposite.so.1.0.0 ./libXcomposite.so.1
    cp -f ${xorg.libXrender}/lib/libXrender.so.1.3.0 ./libXrender.so.1
    cp -f ${xorg.libXcursor}/lib/libXcursor.so.1.0.2 ./libXcursor.so.1
    cp -f ${xorg.libXdamage}/lib/libXdamage.so.1.1.0 ./libXdamage.so.1
    cp -f ${xorg.libXfixes}/lib/libXfixes.so.3.1.0 ./libXfixes.so.3
    cp -f ${xorg.libXi}/lib/libXi.so.6.1.0 ./libXi.so.6
    cp -f ${xorg.libXtst}/lib/libXtst.so.6.1.0 ./libXtst.so.6
    cp -f ${expat}/lib/libexpat.so.1.6.8 ./libexpat.so.1
    cp -f ${utillinux.out}/lib/libuuid.so.1.3.0 ./libuuid.so.1
    cp -f ${xorg.libXrandr.out}/lib/libXrandr.so.2.2.0 ./libXrandr.so.2
    cp -f ${xorg.libXScrnSaver}/lib/libXss.so.1.0.0 libXss.so.1
    cp -f ${alsaLib}/lib/libasound.so.2.0.0 ./libasound.so.2
    cp -f ${at-spi2-atk}/lib/libatk-bridge-2.0.so.0.0.0 libatk-bridge-2.0.so.0
    cp -f ${at-spi2-core}/lib/libatspi.so.0.0.1 ./libatspi.so.0
    cp -f ${cups.lib}/lib/libcups.so.2 ./libcups.so.2
  }
  export -f patchElectron
  '';
}
