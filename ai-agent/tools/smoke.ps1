param(
    [string]$MallUrl = 'http://127.0.0.1:8080',
    [string]$LogisticsUrl = 'http://127.0.0.1:8081'
)

$ErrorActionPreference = 'Stop'

function Login-Mall($name, $password) {
    $webSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $sessionInfo = Invoke-RestMethod -Uri "$MallUrl/session" -WebSession $webSession
    Invoke-WebRequest -Uri "$MallUrl/login" -WebSession $webSession -Method Post `
        -Body @{ username = $name; password = $password; _csrf = $sessionInfo.csrfToken } `
        -MaximumRedirection 1 -SkipHttpErrorCheck | Out-Null
    $sessionInfo = Invoke-RestMethod -Uri "$MallUrl/session" -WebSession $webSession
    if ($sessionInfo.user -ne $name) { throw "商城账号 $name 登录失败" }
    return @{ WebSession = $webSession; Csrf = $sessionInfo.csrfToken }
}

function Mall-Post($path, $login, $body, $key) {
    $headers = @{ 'X-XSRF-TOKEN' = $login.Csrf }
    if ($key) { $headers['Idempotency-Key'] = $key }
    $args = @{ Uri = "$MallUrl$path"; WebSession = $login.WebSession; Method = 'Post'; Headers = $headers }
    if ($null -ne $body) {
        $args.ContentType = 'application/json'
        $args.Body = ($body | ConvertTo-Json -Compress)
    }
    return Invoke-RestMethod @args
}

$merchant = Login-Mall 'merchant' 'merchant123'
$customer = Login-Mall 'customer' 'customer123'
$product = Mall-Post '/admin/products' $merchant @{ name = '冒烟测试商品'; description = '端到端验证'; priceCents = 2999; initialStock = 3 } $null
Mall-Post "/admin/products/$($product.id)/publish" $merchant $null $null | Out-Null
$order = Mall-Post '/orders' $customer @{ productId = $product.id; quantity = 1 } ([guid]::NewGuid().ToString())
Mall-Post "/orders/$($order.id)/payments" $customer $null $null | Out-Null
$shipped = Mall-Post "/admin/orders/$($order.id)/shipments" $merchant $null $null

$logisticsSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$loginPage = Invoke-WebRequest -Uri "$LogisticsUrl/login" -WebSession $logisticsSession
$token = [regex]::Match($loginPage.Content, 'name="_csrf"[^>]*value="([^"]+)"').Groups[1].Value
if (!$token) { throw '物流登录页没有 CSRF token' }
Invoke-WebRequest -Uri "$LogisticsUrl/login" -WebSession $logisticsSession -Method Post `
    -Body @{ username = 'logistics'; password = 'logistics123'; _csrf = $token } `
    -MaximumRedirection 1 -SkipHttpErrorCheck | Out-Null
$page = Invoke-WebRequest -Uri "$LogisticsUrl/" -WebSession $logisticsSession
$token = [regex]::Match($page.Content, 'name="_csrf"[^>]*value="([^"]+)"').Groups[1].Value
if (!$token) { throw '物流录入页没有 CSRF token' }
Invoke-WebRequest -Uri "$LogisticsUrl/events" -WebSession $logisticsSession -Method Post `
    -Body @{ trackingNo = $shipped.trackingNo; status = 'PICKED_UP'; location = '香港分拨中心'; _csrf = $token } | Out-Null

$timeline = Invoke-RestMethod -Uri "$MallUrl/orders/$($order.id)/tracking" -WebSession $customer.WebSession
if ($timeline.Count -lt 1 -or $timeline[0].status -ne 'PICKED_UP') {
    throw '物流事件未出现在商城订单时间线'
}

Write-Output "PASS product=$($product.id) order=$($order.id) tracking=$($shipped.trackingNo) events=$($timeline.Count)"
