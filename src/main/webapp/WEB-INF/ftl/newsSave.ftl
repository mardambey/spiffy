<html> 
<head> 
<title>Spiffy!</title>
</head>

<#if errors??>

<div id="errors">
    <ul>
    	<#list errors as msg>
        <li>Error: ${msg}</li>
    	</#list>
    </ul>
</div>

<#else>

<div>
    You added item ${newsId}!
</div>

</#if>

<span><a href="/spiffy/news/">Main page</a></span>

</body>
</html>
