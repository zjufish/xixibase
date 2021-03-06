xixibaseDIR=$(pwd)
cd 3rd
tar xvzf openssl-1.0.0e.tar.gz
cd openssl-1.0.0e/
./config
make
cp -f  libcrypto.a ../openssl/lib64/.
cp -f  libssl.a ../openssl/lib64/.
cd ../boost 
chmod +x bootstrap.sh
chmod +x tools/build/v2/engine/build.sh
./bootstrap.sh
./bjam address-model=64 --with-date_time --with-system --with-thread --with-filesystem variant=release link=static threading=multi runtime-link=static stage
./bjam address-model=64 $xixibaseDIR -d2 variant=release link=static threading=multi runtime-link=static    
cd $xixibaseDIR
rm -rf dist
mkdir dist
mkdir dist/xixibase
mkdir dist/xixibase/bin
cp bin/xixibase dist/xixibase/bin/.
mkdir dist/xixibase/conf
cp conf/web.xml dist/xixibase/conf/.
cp conf/server.xml dist/xixibase/conf/.
cp conf/privkey.pem dist/xixibase/conf/.
cp conf/cacert.pem dist/xixibase/conf/.
mkdir dist/xixibase/webapps
cp -rf webapps/* dist/xixibase/webapps/.
cd dist
tar cvzf xixibase-ver-os-bin.tar.gz xixibase
cd ..
