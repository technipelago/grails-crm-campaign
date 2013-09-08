<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <title>Unsubscribe</title>
    <g:if test="${css}">
        <style type="text/css">
        ${
        css
        }
        </style>
    </g:if>
</head>

<body>
<div style="width: 400px; margin: 100px auto; padding: 20px; border: 2px solid #999;background-color: #db3835;">
    <h2>${cfg.company?.encodeAsHTML()}</h2>
    <g:if test="${cfg.logo}">
        <img src="${cfg.logo}" alt="${cfg.company?.encodeAsHTML()}"/>
    </g:if>
    <h1>Avregistrering</h1>
    <g:if test="${recipient.dateOptOut}">
        <h3>${recipient.email?.encodeAsHTML()}</h3>
        <h4>är avregistrerad från framtida utskick.</h4>
    </g:if>
    <g:else>
        <g:form action="optout">
            <g:hiddenField name="id" value="${recipient.guid}" id="recipient"/>
            <g:if test="${opts}">
                <h3>Jag vill <span style="text-decoration: underline;">inte</span> längre få:</h3>

                <div style="text-align: left;">
                    <g:each in="${opts}" var="o" status="i">
                        <g:checkBox name="opts" value="${o.key}" checked=""
                                    id="opt${i + 1}"/> ${o.value.encodeAsHTML()}<br/>
                    </g:each>
                </div>

                <h3 style="margin-top: 20px;">skickat till ${recipient.email?.encodeAsHTML()}</h3>
            </g:if>
            <g:else>
                <h3>${recipient.email?.encodeAsHTML()}</h3>
            </g:else>
            <button type="submit">Verkställ</button>
        </g:form>
    </g:else>
    <g:if test="${cfg.website}">
        <a href="${cfg.website}">${(cfg.company ?: 'Home').encodeAsHTML()}</a>
    </g:if>
</div>
</body>
</html>
