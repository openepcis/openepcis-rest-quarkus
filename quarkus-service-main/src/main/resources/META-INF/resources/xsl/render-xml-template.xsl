<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="html" encoding="UTF-8" indent="no"/>
    <xsl:strip-space elements="*"/>

    <xsl:template match="/">
        <html><head>
            <style>
                body { font-family: Arial, sans-serif; font-size: 14px; background: #fff; color: #333; white-space: pre; }
                .tag { color: #006699; }
                .attr { color: #990000; }
                .val { color: #003300; }
                .indent { margin-left: 20px; display: block; }
                .selfclose { color: #999; font-style: italic; }
            </style>
        </head><body><xsl:apply-templates/></body></html>
    </xsl:template>

    <xsl:template match="*">
        <div class="indent"><xsl:choose>

            <!-- Self-closing tag for empty elements -->
            <xsl:when test="not(*) and not(normalize-space())">
                <span class="selfclose">&lt;<xsl:value-of select="name()"/><xsl:if test="@*"><xsl:text> </xsl:text></xsl:if>
                    <xsl:for-each select="@*">
                        <xsl:if test="position() &gt; 1"><xsl:text> </xsl:text></xsl:if>
                        <span class="attr">
                            <xsl:value-of select="name()"/>=<xsl:text>"</xsl:text>
                            <span class="tag"><xsl:value-of select="."/></span>
                            <xsl:text>"</xsl:text>
                        </span>
                    </xsl:for-each>
                    <span class="selfclose">/&gt;</span></span>
            </xsl:when>

            <!-- Element with text only -->
            <xsl:when test="not(*)">
                <span class="tag">&lt;<xsl:value-of select="name()"/><xsl:if test="@*"><xsl:text> </xsl:text></xsl:if>
                    <xsl:for-each select="@*">
                        <xsl:if test="position() &gt; 1"><xsl:text> </xsl:text></xsl:if>
                        <span class="attr">
                            <xsl:value-of select="name()"/>=<xsl:text>"</xsl:text>
                            <span class="tag"><xsl:value-of select="."/></span>
                            <xsl:text>"</xsl:text>
                        </span>
                    </xsl:for-each>
                    <span class="tag">&gt;</span></span>
                <span class="val"><xsl:value-of select="normalize-space(.)"/></span>
                <span class="tag">&lt;/<xsl:value-of select="name()"/>&gt;</span>
            </xsl:when>

            <!-- Elements with children -->
            <xsl:otherwise>
                <span class="tag">&lt;<xsl:value-of select="name()"/><xsl:if test="@*"><xsl:text> </xsl:text></xsl:if>
                    <xsl:for-each select="@*">
                        <xsl:if test="position() &gt; 1"><xsl:text> </xsl:text></xsl:if>
                        <span class="attr">
                            <xsl:value-of select="name()"/>=<xsl:text>"</xsl:text>
                            <span class="tag"><xsl:value-of select="."/></span>
                            <xsl:text>"</xsl:text>
                        </span>
                    </xsl:for-each>
                    <span class="tag">&gt;</span></span>
                <xsl:apply-templates/>
                <span class="tag">&lt;/<xsl:value-of select="name()"/>&gt;</span>
            </xsl:otherwise>

        </xsl:choose></div>
    </xsl:template>

    <!-- Only render meaningful text content -->
    <xsl:template match="text()[normalize-space()]">
        <xsl:value-of select="normalize-space(.)"/>
    </xsl:template>

</xsl:stylesheet>
