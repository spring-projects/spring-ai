-- Exit on any errors
WHENEVER SQLERROR EXIT SQL.SQLCODE

-- Configure the size of the Vector Pool to 1 GiB.
ALTER SYSTEM SET vector_memory_size=1G SCOPE=SPFILE;

SHUTDOWN ABORT;
STARTUP;

exit;
