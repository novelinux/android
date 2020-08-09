Interceptor.attach(Module.findExportByName('libsqlite.so', 'sqlite3_prepare16_v2'), {
      onEnter: function(args) {
            try {
                var sql = Memory.readUtf16String(args[1]);
                if (sql.startsWith('CREATE') || sql.startsWith('INSERT') || sql.startsWith('DELETE') || sql.startsWith('UPDATE') || sql.startsWith('SELECT')) {
                        console.log(new Date().toISOString() + ': ' + sql);
                }
           } catch (e) {}
      }
});
