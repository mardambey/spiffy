<?xml version="1.0"?>
<response value="${response}"/>
<#if errors??>
<errors>
	<#list errors as msg>
	<error msg="${msg}"/>
	</#list>
</errors>
</#if>
