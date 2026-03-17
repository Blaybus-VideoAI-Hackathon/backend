@echo off
echo === BCrypt Password Generator ===
echo.

echo Generating BCrypt hash for password "1111"...
echo.

powershell -Command "
Add-Type -AssemblyName System.Web;
$password = '1111';
$salt = [System.Web.Security.Membership]::GenerateSalt(16);
$hash = [System.Web.Security.FormsAuthentication]::HashPasswordForStoringInConfigFile($password + $salt, 'SHA1');
echo 'BCrypt-style hash (SHA1+Salt): ' + $hash;
echo 'Salt: ' + $salt;
echo.
echo 'Note: This is not true BCrypt but similar hashing approach';
echo 'For true BCrypt, use the Spring Boot application or online BCrypt generator';
"

pause
